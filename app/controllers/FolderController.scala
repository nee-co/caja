package controllers

import javax.inject.Inject

import play.api.mvc.{Action, Controller}
import services.DBService

class FolderController @Inject()(db: DBService) extends Controller {
  def init = Action(parse.multipartFormData) { implicit request =>
    val groupId = request.body.dataParts("group_id").headOption.map(_.toInt)

    (groupId.nonEmpty, db.hasTop(groupId.get)) match {
      case (true,  true) => Status(204)
      case (true, false) => if (db.addFolder(0, groupId.get, 0, "top")) Status(204) else Status(500)
      case (   _,     _) => Status(500)
    }
  }

  def clean = Action(parse.multipartFormData) { implicit request =>
    val groupId = request.body.dataParts("group_id").headOption.map(_.toInt)
    groupId.fold(Status(204))(id => if (db.clean(id)) Status(204) else Status(500))
  }
}
