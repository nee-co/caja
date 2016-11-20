package controllers

import java.io.{File, FileOutputStream}
import javax.inject.Inject

import models.User
import play.api.mvc.{Action, Controller}
import services.{DBService, S3Service, WsService}
import utils.{JsonFormatter, Using}

import scala.collection.mutable

class FileController @Inject()(db: DBService, ws: WsService, s3: S3Service, formatter: JsonFormatter) extends Controller {
  def upload = Action(parse.multipartFormData) { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val parentId = request.body.dataParts("parent_id").headOption
    val file = request.body.file("file")
    val canCreate = db.canCreateAndRead(parentId, ws.groups(loginId.fold(0)(identity)))
    val hasIdenticalName = db.hasIdenticalName(parentId, Some(file.fold("")(_.filename)))

    (parentId, loginId, file, canCreate, hasIdenticalName) match {
      case (Some(p1), Some(p2), Some(p3),  true, false) =>
        val id = db.uuid
        val parent = db.getFolder(p1).get

        (s3.upload(s"${parent.groupId}/", id, p3.ref.file), db.createFile(id, parent.id, p2, p3.filename, s"${parent.groupId}/$id")) match {
          case ( true, Some(createdId)) =>
            val createdFile = db.getFile(id)
            val users = new mutable.HashMap[Int, User]
            val userIds = createdFile.map(_.insertedBy).toSeq ++ createdFile.map(_.updatedBy).toSeq

            ws.users(userIds.distinct).foreach(user => users.put(user.user_id, user))

            formatter.toFileJson(createdFile, users.toMap) match {
              case Some(jsValue) => Created(jsValue)
              case None => InternalServerError
            }
          case (false, Some(createdId)) => db.deleteFile(createdId); InternalServerError
          case ( true,            None) => s3.delete(s"${parent.groupId}/$id"); InternalServerError
          case _ => InternalServerError
        }
      case (Some(p1), Some(p2), Some(p3), false, false) => db.getFolder(parentId.fold("")(identity)).fold(Status(404))(folder => Status(403))
      case (       _, Some(p2),        _,     _,     _) => Status(422)
      case _ => Status(500)
    }
  }

  def download(id: String) = Action { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val file = db.getFile(id)
    val canRead = db.canReadFile(Some(id), ws.groups(loginId.fold(0)(identity)))

    (loginId, file, canRead, s3.download(file.fold("")(file => s"${file.groupId}/${file.id}"))) match {
      case (Some(p1), Some(p2),  true, Some(p3)) =>
        val temp: File = File.createTempFile("temp", "")
        for (out <- Using(new FileOutputStream(temp))) out.write(p3)
        Ok.sendFile(temp, fileName = {f => file.get.name}, onClose = {() => temp.delete})
      case (Some(p1), Some(p2), false,          _) => Status(403)
      case (Some(p1),     None,     _,          _) => Status(404)
      case _ => Status(500)
    }
  }

  def update(id: String) = Action(parse.multipartFormData) { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val name = request.body.dataParts("name").headOption
    val file = db.getFile(id)
    val canUpdate = db.canUpdateAndDeleteFile(id, loginId)

    (loginId, name, file, canUpdate) match {
      case (Some(p1), Some(p2), Some(p3), true) =>
        val updatedId = db.updateFile(file, p1, p2)
        val updatedFile = db.getFile(updatedId.fold("")(identity))
        val users = new mutable.HashMap[Int, User]
        val userIds = updatedFile.map(_.insertedBy).toSeq ++ updatedFile.map(_.updatedBy).toSeq

        ws.users(userIds.distinct).foreach(user => users.put(user.user_id, user))

        formatter.toFileJson(updatedFile, users.toMap) match {
          case Some(jsValue) => Ok(jsValue)
          case None => InternalServerError
        }
      case (Some(p1), Some(p2), Some(p3), false) => Status(403)
      case (Some(p1), Some(p2),     None,     _) => Status(404)
      case (Some(p1),     None, Some(p3),     _) => Status(422)
      case _ => Status(500)
    }
  }
}
