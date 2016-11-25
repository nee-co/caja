package controllers

import javax.inject.Inject

import models.User
import play.api.mvc.{Action, Controller}
import services.{DBService, WsService}
import utils.JsonFormatter

import scala.collection.mutable

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
        val users = new mutable.HashMap[Int, User]
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

  def update(id: String) = Action(parse.multipartFormData) { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val name = request.body.dataParts("name").headOption
    val folder = db.getFolder(id)
    val canUpdate = db.canUpdateAndDeleteFolder(id, loginId)

    (loginId, name, folder, canUpdate) match {
      case (Some(p1), Some(p2), Some(p3),  true) =>
        val updatedId = db.updateFolder(folder, p1, p2)
        val updatedFolder = db.getFolder(updatedId.fold("")(identity))
        val users = new mutable.HashMap[Int, User]
        val userIds = updatedFolder.map(_.insertedBy).toSeq ++ updatedFolder.map(_.updatedBy).toSeq

        ws.users(userIds.distinct).foreach(user => users.put(user.user_id, user))

        formatter.toFolderJson(updatedFolder, users.toMap) match {
          case Some(jsValue) => Ok(jsValue)
          case None => InternalServerError
        }
      case (Some(p1), Some(p2), Some(p3), false) => Status(403)
      case (Some(p1), Some(p2),     None,     _) => Status(404)
      case _ => Status(500)
    }
  }

  def delete(id: String) = Action { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val folder = db.getFolder(id)
    val canDelete = db.canUpdateAndDeleteFolder(id, loginId)

    (loginId, folder, canDelete) match {
      case (Some(p1), Some(p2), true) =>
        db.deleteUnderElements(id)
        db.updateFolders(folder.fold("")(_.parentId), loginId.fold(0)(identity))
        NoContent
      case (Some(p1), Some(p2), false) => Status(403)
      case (Some(p1),     None,     _) => Status(404)
      case _ => Status(500)
    }
  }

  def elements(id: String) = Action { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val users = new mutable.HashMap[Int, User]
    val canRead = db.canCreateAndRead(Some(id), ws.groups(loginId.fold(0)(identity)))
    val hasFolder = db.getFolder(id).nonEmpty
    val current = db.getFolder(id)
    val elements = db.getUnderElements(id)
    val userIds = (current.map(_.insertedBy) ++ current.map(_.updatedBy) ++ elements.map(_.insertedBy) ++ elements.map(_.updatedBy)).toSeq

    ws.users(userIds.distinct).foreach(user => users.put(user.user_id, user))

    val json = formatter.toUnderCollectionJson(current, elements, users.toMap)

    (canRead, hasFolder, json) match {
      case ( true,  true, Some(jsValue)) => Ok(jsValue)
      case (false,  true,             _) => Status(403)
      case (    _, false,             _) => Status(404)
      case _ => Status(500)
    }
  }

  def tops = Action { implicit request =>
    val loginId = request.headers.get("x-consumer-custom-id").map(_.toInt)
    val users = new mutable.HashMap[Int, User]
    val tops = db.myTops(ws.groups(loginId.fold(0)(identity)))
    ws.users(tops.map(_.updatedBy).distinct.filter(_ != 0)).foreach(user => users.put(user.user_id, user))

    loginId match {
      case Some(p1) =>
        formatter.toTopsJson(tops, users.toMap) match {
          case Some(jsValue) => Ok(jsValue)
          case None => InternalServerError
        }
      case None => Status(500)
    }
  }
}
