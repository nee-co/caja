package utils

import models.Tables.FoldersRow
import models._
import org.joda.time.DateTime
import play.api.libs.json.{JsValue, Json}

class JsonFormatter {
  def toTopsJson(tops: Seq[FoldersRow], users: Map[Int, User], groups: Map[String, Group]): Option[JsValue] = {
    val format = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    Some(Json.toJson(
      Tops(tops.map(top => {
        val insertedAt = new DateTime(top.insertedAt).toString(format)
        val updatedAt = new DateTime(top.updatedAt).toString(format)
        Top(top.id, groups(top.groupId).name, groups(top.groupId).image.getOrElse(""), users(top.insertedBy), insertedAt, users(top.updatedBy), updatedAt)
      }))
    ))
  }

  def toUnderCollectionJson(current: Option[ObjectProperty], elements: Seq[ObjectProperty], users: Map[Int, User], parents: Seq[Parent]): Option[JsValue] = {
    val result = for {
      current <- current
    } yield {
      val currentFolder = Folder(current.id, current.name, users(current.insertedBy), current.insertedAt, users(current.updatedBy), current.updatedAt)
      val elementList = elements.map(obj => Element(obj.`type`, obj.id, obj.name, users(obj.insertedBy), obj.insertedAt, users(obj.updatedBy), obj.updatedAt, obj.size))

      Json.toJson(Elements(parents, currentFolder, elementList))
    }
    result.iterator.toStream.headOption
  }

  def toFileJson(file: Option[ObjectProperty], users: Map[Int, User]): Option[JsValue] = {
    val result = for {
      file <- file
    } yield {
      Json.toJson(File(file.id, file.name, users(file.insertedBy), file.insertedAt, users(file.updatedBy), file.updatedAt, file.size.getOrElse(0)))
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
