package controllers

import javax.inject.Inject

import models.User
import play.api.mvc.{Action, Controller}
import services.{DBService, WsService}
import utils.JsonFormatter

class FolderController @Inject()(db: DBService, ws: WsService, formatter: JsonFormatter) extends Controller {
  def init = Action(parse.multipartFormData) { implicit request =>
    val groupId = request.body.dataParts("group_id").headOption

    (groupId.nonEmpty, db.hasTop(groupId.get)) match {
      case (true,  true) => NoContent
      case (true, false) =>
        db.createFolder("0", groupId.get, 0, "top") match {
          case Some(createdId) => NoContent
          case None => InternalServerError
        }
      case _ => InternalServerError
    }
  }

  def clean = Action(parse.multipartFormData) { implicit request =>
    val groupId = request.body.dataParts("group_id").headOption
    groupId.fold(Status(204))(id => if (db.clean(id)) Status(204) else Status(500))
  }

  def create = Action(parse.multipartFormData) { implicit request => 
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt) 
    val name = request.body.dataParts("name").headOption 
    val parentId = request.body.dataParts("parent_id").headOption 
    val canCreate = db.canCreateAndRead(parentId, ws.groups(loginId.fold(0)(identity)))  

    (parentId, loginId, name, canCreate, db.hasIdenticalName(parentId, name)) match { 
      case (Some(p1), Some(p2), Some(p3),  true, false) => 
        val createdId = db.createFolder(p1, "", p2, p3) 
        val createdFolder = db.getFolder(createdId.fold("")(identity)) 
        val users = new scala.collection.mutable.HashMap[Int, User] 
        val userIds = createdFolder.map(_.insertedBy).toSeq ++ createdFolder.map(_.updatedBy).toSeq  

        ws.users(userIds.distinct).foreach(user => users.put(user.user_id, user))  

        formatter.toFolderJson(createdFolder, users.toMap) match { 
          case Some(jsValue) => Created(jsValue) 
          case None => InternalServerError 
        } 
      case (Some(p1), Some(p2), Some(p3), false, false) => Status(403) 
      case (       _, Some(p2),        _,     _,     _) => Status(422) 
      case _ => Status(500) 
    } 
  }
}
