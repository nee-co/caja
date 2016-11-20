package controllers

import javax.inject.Inject

import play.api.mvc.{Action, Controller}
import services.DBService

class FolderController @Inject()(db: DBService) extends Controller {
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
}
