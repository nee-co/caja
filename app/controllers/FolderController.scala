package controllers

import javax.inject.Inject

import models.{Group, Parent, User}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, Controller}
import services.{DBService, WsService}
import utils.{JsonFormatter, MyAction}

import scala.collection.mutable

class FolderController @Inject()(db: DBService, ws: WsService, formatter: JsonFormatter) extends Controller {
  def init = Action(parse.urlFormEncoded) { implicit request =>
    (for {
      groupId <- request.body.get("group_id").flatMap(_.headOption).toRight(InternalServerError).right
      userId  <- request.body.get("user_id").flatMap(_.headOption).flatMap(safeStringToInt).toRight(InternalServerError).right
      result  <- {
        if (db.hasTop(groupId)) {
          None.toRight(NoContent).right
        } else {
          db.createFolder("0", groupId, userId.toInt, "top").toRight(InternalServerError).right
        }
      }
    } yield result).fold(identity, createdId => NoContent)
  }

  def topId(groupId: String) = Action { implicit request =>
    db.getTop(groupId) match {
      case Some(folder) => Ok(Json.parse(s"""{"id":"${folder.id}"}"""))
      case None => NotFound
    }
  }

  def clean = Action(parse.urlFormEncoded) { implicit request =>
    val groupId = request.body.get("group_id").flatMap(_.headOption)
    groupId.fold(NoContent)(id => if (db.clean(id)) NoContent else InternalServerError)
  }

  private def createFolder(parentId: String, name: String, loginId: Int): Option[JsValue] = {
    val createdId = db.createFolder(parentId, "", loginId, name)
    val createdFolder = db.getFolder(createdId.fold(return None)(identity))
    val users = new mutable.HashMap[Int, User]
    val userIds = createdFolder.map(_.insertedBy).toSeq ++ createdFolder.map(_.updatedBy).toSeq

    ws.users(userIds.distinct).foreach(user => users.put(user.id, user))
    formatter.toFolderJson(createdFolder, users.toMap)
  }

  def create = MyAction.inside(parse.urlFormEncoded) { implicit request =>
    (for {
      name     <- request.body.get("name").flatMap(_.headOption).toRight(UnprocessableEntity).right
      parentId <- request.body.get("parent_id").flatMap(_.headOption).toRight(UnprocessableEntity).right
      result   <- {
        if (name == "" || parentId == "") {
          None.toRight(UnprocessableEntity).right
        } else if (!db.canCreateAndRead(parentId, ws.groups(request.loginId).map(_.id))) {
          None.toRight(Forbidden).right
        } else if (!db.isUsableName(parentId, name)) {
          None.toRight(UnprocessableEntity).right
        } else {
          createFolder(parentId, name, request.loginId).toRight(InternalServerError).right
        }
      }
    } yield result).fold(identity, jsValue => Created(jsValue))
  }

  def update(id: String) = MyAction.inside(parse.urlFormEncoded) { implicit request =>
    (for {
      folder <- db.getFolder(id).toRight(NotFound).right
      name   <- request.body.get("name").flatMap(_.headOption).toRight(UnprocessableEntity).right
      result <- {
        if (name == "") {
          None.toRight(UnprocessableEntity).right
        } else if (!db.canUpdateAndDeleteFolder(id, request.loginId)) {
          None.toRight(Forbidden).right
        } else {
          db.updateFolder(folder, request.loginId, name).toRight(InternalServerError).right
        }
      }
    } yield result).fold(identity, updatedId => NoContent)
  }

  def delete(id: String) = MyAction.inside { implicit request =>
    (for {
      folder <- db.getFolder(id).toRight(NotFound).right
    } yield {
      if (db.canUpdateAndDeleteFolder(id, request.loginId)) {
        db.deleteUnderElements(id)
        db.updateFolders(folder.parentId, request.loginId)
        NoContent
      } else {
        Forbidden
      }
    }).fold(identity, identity)
  }

  def elements(id: String) = MyAction.inside { implicit request =>
    (for {
      current <- db.getFolder(id).toRight(NotFound).right
      result  <- {
        if (db.canCreateAndRead(current.id, ws.groups(request.loginId).map(_.id))) {
          val users = new mutable.HashMap[Int, User]
          val elements = db.getUnderElements(id)
          val userIds = Seq(current.insertedBy, current.updatedBy) ++ elements.map(_.insertedBy) ++ elements.map(_.updatedBy)
          val groups  = ws.groups(request.loginId)
                          .map(group => Map(group.id -> group.name))
                          .reduce((a, b) => a ++ b)
          val parents = db.getParents(id)
                          .map(folder =>
                            if (folder.name == "top") {
                              Parent(folder.id, groups(folder.groupId))
                            } else {
                              Parent(folder.id, folder.name)
                            })
                          .reverse
          ws.users(userIds.distinct).foreach(user => users.put(user.id, user))
          formatter.toUnderCollectionJson(current, elements, users.toMap, parents).toRight(InternalServerError).right
        } else {
          None.toRight(Forbidden).right
        }
      }
    } yield result).fold(identity, jsValue => Ok(jsValue))
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

  private def safeStringToInt(str: String): Option[Int] = {
    import scala.util.control.Exception._
    catching(classOf[NumberFormatException]) opt str.toInt
  }
}
