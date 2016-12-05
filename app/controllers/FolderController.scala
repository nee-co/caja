package controllers

import javax.inject.Inject

import models.{Group, User}
import play.api.mvc.{Action, Controller}
import services.{DBService, WsService}
import utils.{JsonFormatter, MyAction}

import scala.collection.mutable

class FolderController @Inject()(db: DBService, ws: WsService, formatter: JsonFormatter) extends Controller {
  def init = Action(parse.urlFormEncoded) { implicit request =>
    val groupId = request.body("group_id").headOption

    (groupId.nonEmpty, db.hasTop(groupId.get)) match {
      case (true,  true) => NoContent
      case (true, false) =>
        db.createFolder("0", groupId.get, 0, "top") match {
          case Some(createdId) => NoContent
          case None => InternalServerError
        }
      case _ => Status(500)
    }
  }

  def clean = Action(parse.urlFormEncoded) { implicit request =>
    val groupId = request.body("group_id").headOption
    groupId.fold(Status(204))(id => if (db.clean(id)) Status(204) else Status(500))
  }

  def create = MyAction.inside(parse.urlFormEncoded) { implicit request =>
    val name = request.body("name").headOption
    val parentId = request.body("parent_id").headOption
    val canCreate = db.canCreateAndRead(parentId, ws.groups(request.loginId).map(_.id))

    (parentId, name, canCreate, db.hasIdenticalName(parentId, name)) match {
      case (Some(p1), Some(p2),  true, false) =>
        val createdId = db.createFolder(p1, "", request.loginId, p2)
        val createdFolder = db.getFolder(createdId.fold("")(identity))
        val users = new mutable.HashMap[Int, User]
        val userIds = createdFolder.map(_.insertedBy).toSeq ++ createdFolder.map(_.updatedBy).toSeq

        ws.users(userIds.distinct).foreach(user => users.put(user.id, user))

        formatter.toFolderJson(createdFolder, users.toMap) match {
          case Some(jsValue) => Created(jsValue)
          case None => InternalServerError
        }
      case (Some(p1), Some(p2), false, false) => Status(403)
      case (       _,        _,     _,     _) => Status(422)
      case _ => Status(500)
    }
  }

  def update(id: String) = MyAction.inside(parse.urlFormEncoded) { implicit request =>
    val folder = db.getFolder(id)
    val name = request.body("name").headOption
    val canUpdate = db.canUpdateAndDeleteFolder(id, request.loginId)

    (folder, name, canUpdate) match {
      case (Some(p1), Some(p2),  true) =>
        val updatedId = db.updateFolder(p1, request.loginId, p2)
        val updatedFolder = db.getFolder(updatedId.fold("")(identity))
        val users = new mutable.HashMap[Int, User]
        val userIds = updatedFolder.map(_.insertedBy).toSeq ++ updatedFolder.map(_.updatedBy).toSeq

        ws.users(userIds.distinct).foreach(user => users.put(user.id, user))

        formatter.toFolderJson(updatedFolder, users.toMap) match {
          case Some(jsValue) => Ok(jsValue)
          case None => InternalServerError
        }
      case (Some(p1), Some(p2), false) => Status(403)
      case (    None, Some(p2),     _) => Status(404)
      case _ => Status(500)
    }
  }

  def delete(id: String) = MyAction.inside { implicit request =>
    val folder = db.getFolder(id)
    val canDelete = db.canUpdateAndDeleteFolder(id, request.loginId)

    (folder, canDelete) match {
      case (Some(p1),  true) =>
        db.deleteUnderElements(id)
        db.updateFolders(p1.parentId, request.loginId)
        NoContent
      case (Some(p1), false) => Status(403)
      case (    None,     _) => Status(404)
      case _ => Status(500)
    }
  }

  def elements(id: String) = MyAction.inside { implicit request =>
    val users = new mutable.HashMap[Int, User]
    val canRead = db.canCreateAndRead(Some(id), ws.groups(request.loginId).map(_.id))
    val hasFolder = db.getFolder(id).nonEmpty
    val current = db.getFolder(id)
    val elements = db.getUnderElements(id)
    val userIds = (current.map(_.insertedBy) ++ current.map(_.updatedBy) ++ elements.map(_.insertedBy) ++ elements.map(_.updatedBy)).toSeq
    ws.users(userIds.distinct).foreach(user => users.put(user.id, user))
    val json = formatter.toUnderCollectionJson(current, elements, users.toMap)

    (canRead, hasFolder, json) match {
      case ( true,  true, Some(jsValue)) => Ok(jsValue)
      case (false,  true,             _) => Status(403)
      case (    _, false,             _) => Status(404)
      case _ => Status(500)
    }
  }

  def tops = MyAction.inside { implicit request =>
    val users = new mutable.HashMap[Int, User]
    val groups = new mutable.HashMap[String, Group]
    ws.groups(request.loginId).foreach(group => groups.put(group.id, group))
    val tops = db.myTops(groups.keysIterator.toSeq)
    ws.users((tops.map(_.insertedBy) ++ tops.map(_.updatedBy)).distinct).foreach(user => users.put(user.id, user))

    formatter.toTopsJson(tops, users.toMap, groups.toMap) match {
      case Some(jsValue) => Ok(jsValue)
      case None => InternalServerError
    }
  }
}
