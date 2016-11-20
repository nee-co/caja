package models

import play.api.libs.json.Json

case class College(code: String, name: String)
object College {
  implicit def jsonReads  = Json.reads[College]
  implicit def jsonWrites = Json.writes[College]
}

case class Group(group_id: String, name: String, note: String, is_private: Boolean, group_image: Option[String])
object Group {
  implicit def jsonReads  = Json.reads[Group]
  implicit def jsonWrites = Json.writes[Group]
}

case class Groups(groups: Seq[Group])
object Groups {
  implicit def jsonReads  = Json.reads[Groups]
  implicit def jsonWrites = Json.writes[Groups]
}

case class User(user_id: Int, name: String, number: String, note: String, user_image: String, college: College)
object User {
  implicit def jsonReads  = Json.reads[User]
  implicit def jsonWrites = Json.writes[User]
}

case class Users(users: Seq[User])
object Users {
  implicit def jsonReads  = Json.reads[Users]
}

case class File(id: String, name: String, created_user: User, created_at: String, updated_user: User, updated_at: String)
object File {
  implicit def jsonWrites = Json.writes[File]
}

case class Folder(id: String, name: String, created_user: User, created_at: String, updated_user: User, updated_at: String)
object Folder {
  implicit def jsonWrites = Json.writes[Folder]
}

case class Folders(folders: Seq[Folder])
object Folders {
  implicit def jsonWrites = Json.writes[Folders]
}

case class Element(`type`: String, id: String, name: String, created_user: User, created_at: String, updatedBy: User, updatedAt: String)
object Element {
  implicit def jsonWrites = Json.writes[Element]
}

case class ResponseHasElements(current_folder: Folder, elements: Seq[Element])
object ResponseHasElements {
  implicit def jsonWrites = Json.writes[ResponseHasElements]
}