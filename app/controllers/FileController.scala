package controllers

import java.io.{File, FileOutputStream}
import javax.inject.Inject

import models.{ObjectProperty, Url, User}
import play.api.mvc.{Action, Controller}
import services.{DBService, S3Service, WsService}
import utils.{JsonFormatter, MyAction, Using}

import scala.collection.mutable
import com.redis._
import com.typesafe.config.ConfigFactory
import org.apache.commons.lang3.RandomStringUtils
import play.api.libs.json.{JsValue, Json}

class FileController @Inject()(db: DBService, ws: WsService, s3: S3Service, formatter: JsonFormatter) extends Controller {
  val config = ConfigFactory.load
  val redis = new RedisClient(config.getString("redis.host"), config.getInt("redis.port"))

  private def uploadFile(id: String, loginId: Int, parent: ObjectProperty, filename: String, file: File): Option[JsValue] = {
    (db.createFile(id, parent.id, loginId, filename, file.length.toInt), s3.upload(id, file)) match {
      case (Some(createdId),  true) =>
        val createdFile = db.getFile(createdId)
        val users = new mutable.HashMap[Int, User]
        val userIds = createdFile.map(_.insertedBy).toSeq ++ createdFile.map(_.updatedBy).toSeq

        ws.users(userIds.distinct).foreach(user => users.put(user.id, user))
        formatter.toFileJson(createdFile, users.toMap)
      case (Some(createdId), false) =>
        db.deleteFile(createdId, loginId)
        None
      case (None, true) =>
        s3.delete(id)
        None
      case _ => None
    }
  }

  def upload = MyAction.inside(parse.multipartFormData) { implicit request =>
    (for {
      parentId <- request.body.dataParts.get("parent_id").flatMap(_.headOption).toRight(UnprocessableEntity).right
      file     <- request.body.file("file").toRight(UnprocessableEntity).right
      parent   <- {
        if (parentId == "") {
          None.toRight(UnprocessableEntity).right
        } else {
          db.getFolder(parentId).toRight(NotFound).right
        }
      }
      result   <- {
        if (!db.canCreateAndRead(parentId, ws.groups(request.loginId).map(_.id))) {
          None.toRight(Forbidden).right
        } else if (!db.isUsableName(parentId, file.filename)) {
          None.toRight(UnprocessableEntity).right
        } else {
          uploadFile(db.uuid, request.loginId, parent, file.filename, file.ref.file).toRight(InternalServerError).right
        }
      }
    } yield result).fold(identity, jsValue => Created(jsValue))
  }

  def downloadUrl(id: String) = MyAction.inside { implicit request =>
    if (db.getFile(id).isEmpty) {
      NotFound
    } else if (db.canReadFile(id, ws.groups(request.loginId).map(_.id))) {
      val token = RandomStringUtils.randomAlphanumeric(32)
      redis.setex(token, config.getInt("redis.expire"), id)
      Ok(Json.toJson(Url(s"${config.getString("api.url")}/download/$id?token=$token")))
    } else {
      Forbidden
    }
  }

  def download(id: String, token: String) = Action { implicit request =>
    (for {
      token        <- redis.get(token).toRight(Forbidden).right
      fileProperty <- db.getFile(id).toRight(NotFound).right
      fileObj      <- s3.download(fileProperty.id).toRight(NotFound).right
      result       <- {
        if (token == id) {
          redis.del(token)
          val temp = File.createTempFile("temp", "")
          for (out <- Using(new FileOutputStream(temp))) out.write(fileObj)
          Some(temp).toRight(InternalServerError).right
        } else {
          None.toRight(Forbidden).right
        }
      }
    } yield {
      Ok.sendFile(result, fileName = { f => fileProperty.name }, onClose = { () => result.delete })
    }).fold(identity, identity)
  }

  def update(id: String) = MyAction.inside(parse.urlFormEncoded) { implicit request =>
    (for {
      name   <- request.body.get("name").flatMap(_.headOption).toRight(UnprocessableEntity).right
      file   <- db.getFile(id).toRight(NotFound).right
      result <- {
        if (name == "") {
          None.toRight(UnprocessableEntity).right
        }else if (db.canUpdateAndDeleteFile(id, request.loginId)) {
          db.updateFile(file, request.loginId, name).toRight(InternalServerError).right
        } else {
          None.toRight(Forbidden).right
        }
      }
    } yield result).fold(identity, updatedId => NoContent)
  }

  def delete(id: String) = MyAction.inside { implicit request =>
    (for {
      file <- db.getFile(id).toRight(NotFound).right
    } yield {
      if (db.canUpdateAndDeleteFile(file.id, request.loginId)) {
        (db.deleteFile(file.id, request.loginId), s3.delete(file.id)) match {
          case (true,  true) => NoContent
          case (true, false) =>
            db.createFile(file.id, file.parentId, request.loginId, file.name, file.size.getOrElse(0))
            InternalServerError
          case _ => InternalServerError
        }
      } else {
        Forbidden
      }
    }).fold(identity, identity)
  }
}
