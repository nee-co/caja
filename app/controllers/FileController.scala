package controllers

import javax.inject.Inject

import models.User
import play.api.mvc.{Action, Controller}
import services.{DBService, S3Service, WsService}
import utils.JsonFormatter

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
}
