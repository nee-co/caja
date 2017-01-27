package controllers

import java.io.{File, FileOutputStream}
import javax.inject.Inject

import models.User
import play.api.mvc.Controller
import services.{DBService, S3Service, WsService}
import utils.{JsonFormatter, MyAction, Using}

import scala.collection.mutable

class FileController @Inject()(db: DBService, ws: WsService, s3: S3Service, formatter: JsonFormatter) extends Controller {
  def upload = MyAction.inside(parse.multipartFormData) { implicit request =>
    val parentId = request.body.dataParts("parent_id").headOption
    val file = request.body.file("file")
    val canCreate = db.canCreateAndRead(parentId, ws.groups(request.loginId).map(_.id))
    val hasIdenticalName = db.hasIdenticalName(parentId, Some(file.fold("")(_.filename)))

    (parentId, file, canCreate, hasIdenticalName) match {
      case (Some(p1), Some(p2),  true, false) =>
        val id = db.uuid
        val parent = db.getFolder(p1).get

        (s3.upload(s"${parent.groupId}/", id, p2.ref.file), db.createFile(id, parent.id, request.loginId, p2.filename, p2.ref.file.length.toInt)) match {
          case (true, Some(createdId)) =>
            val createdFile = db.getFile(createdId)
            val users = new mutable.HashMap[Int, User]
            val userIds = createdFile.map(_.insertedBy).toSeq ++ createdFile.map(_.updatedBy).toSeq

            ws.users(userIds.distinct).foreach(user => users.put(user.id, user))

            formatter.toFileJson(createdFile, users.toMap) match {
              case Some(jsValue) => Created(jsValue)
              case None => InternalServerError
            }
          case (false, Some(createdId)) => db.deleteFile(createdId, request.loginId); InternalServerError
          case (true, None) => s3.delete(s"${parent.groupId}/$id"); InternalServerError
          case _ => InternalServerError
        }
      case (Some(p1), Some(p2), false, false) => db.getFolder(p1).fold(NotFound)(folder => Forbidden)
      case (       _, Some(p2),     _,     _) => UnprocessableEntity
      case _ => InternalServerError
    }
  }

  def download(id: String) = MyAction.inside { implicit request =>
    val file = db.getFile(id)
    val canRead = db.canReadFile(Some(id), ws.groups(request.loginId).map(_.id))

    (file, canRead, s3.download(file.fold("")(file => s"${file.groupId}/${file.id}"))) match {
      case (Some(p1),  true, Some(p3)) =>
        val temp: File = File.createTempFile("temp", "")
        for (out <- Using(new FileOutputStream(temp))) out.write(p3)
        Ok.sendFile(temp, fileName = {f => file.get.name}, onClose = {() => temp.delete})
      case (Some(p1), false,        _) => Forbidden
      case (    None,     _,        _) => NotFound
      case _ => InternalServerError
    }
  }

  def update(id: String) = MyAction.inside(parse.urlFormEncoded) { implicit request =>
    val file = db.getFile(id)
    val name = request.body("name").headOption
    val canUpdate = db.canUpdateAndDeleteFile(id, request.loginId)

    (file, name, canUpdate) match {
      case (Some(p1), Some(p2),  true) =>
        val updatedId = db.updateFile(p1, request.loginId, p2)
        val updatedFile = db.getFile(updatedId.fold("")(identity))
        val users = new mutable.HashMap[Int, User]
        val userIds = updatedFile.map(_.insertedBy).toSeq ++ updatedFile.map(_.updatedBy).toSeq

        ws.users(userIds.distinct).foreach(user => users.put(user.id, user))

        formatter.toFileJson(updatedFile, users.toMap) match {
          case Some(jsValue) => Ok(jsValue)
          case None => InternalServerError
        }
      case (Some(p1), Some(p2), false) => Forbidden
      case (    None, Some(p2),     _) => NotFound
      case (Some(p1),     None,     _) => UnprocessableEntity
      case _ => InternalServerError
    }
  }

  def delete(id: String) = MyAction.inside { implicit request =>
    val file = db.getFile(id)
    val canDelete = db.canUpdateAndDeleteFile(id, request.loginId)

    (file, canDelete) match {
      case (Some(p1),  true) =>
        (s3.delete(s"${p1.groupId}/${p1.id}"), db.deleteFile(id, request.loginId)) match {
          case (true,  true) => NoContent
          case (true, false) => db.createFile(p1.id, p1.parentId, request.loginId, p1.name, p1.size.getOrElse(0)); InternalServerError
          case _ => InternalServerError
        }
      case (Some(p1), false) => Forbidden
      case (    None,     _) => NotFound
      case _ => InternalServerError
    }
  }
}
