package utils

import models.Tables.FoldersRow
import models._
import play.api.libs.json.{JsValue, Json}

class JsonFormatter {
  def toTopsJson(tops: Seq[FoldersRow], groups: Map[String, Group]): Option[JsValue] = {
    Some(Json.toJson(
      Tops(tops.map(top =>
        Top(top.id, groups(top.groupId).name, groups(top.groupId).image.getOrElse(""))
      ))
    ))
  }

  def toUnderCollectionJson(current: Option[ObjectProperty], elements: Seq[ObjectProperty], users: Map[Int, User]): Option[JsValue] = {
    val result = for {
      current <- current
    } yield {
      val currentFolder = Folder(current.id, current.name, users(current.insertedBy), current.insertedAt, users(current.updatedBy), current.updatedAt)
      val elementList = elements.map(obj => Element(obj.`type`, obj.id, obj.name, users(obj.insertedBy), obj.insertedAt, users(obj.updatedBy), obj.updatedAt))

      Json.toJson(Elements(currentFolder, elementList))
    }
    result.iterator.toStream.headOption
  }

  def toFileJson(file: Option[ObjectProperty], users: Map[Int, User]): Option[JsValue] = {
    val result = for {
      file <- file
    } yield {
      Json.toJson(File(file.id, file.name, users(file.insertedBy), file.insertedAt, users(file.updatedBy), file.updatedAt))
    }
    result.iterator.toStream.headOption
  }

  def toFolderJson(folder: Option[ObjectProperty], users: Map[Int, User]): Option[JsValue] = {
    val result = for {
      folder <- folder
    } yield {
      Json.toJson(Folder(folder.id, folder.name, users(folder.insertedBy), folder.insertedAt, users(folder.updatedBy), folder.updatedAt))
    }
    result.iterator.toStream.headOption
  }
}
