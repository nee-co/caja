package models

import play.api.libs.json.Json

case class College(code: String, name: String)
object College {
  implicit def jsonReads  = Json.reads[College]
  implicit def jsonWrites = Json.writes[College]
}

case class Group(id: String, name: String, note: String, is_private: Boolean, image: Option[String])
object Group {
  implicit def jsonReads  = Json.reads[Group]
  implicit def jsonWrites = Json.writes[Group]
}

case class Groups(groups: Seq[Group])
object Groups {
  implicit def jsonReads  = Json.reads[Groups]
  implicit def jsonWrites = Json.writes[Groups]
}

case class User(id: Int, name: String, number: String, note: String, image: String, college: College)
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

case class Top(id: String, name: String, image: String, created_user: User, created_at: String, updated_user: User, updated_at: String)
object Top {
  implicit def jsonWrites = Json.writes[Top]
}

case class Tops(folders: Seq[Top])
object Tops {
  implicit def jsonWrites = Json.writes[Tops]
}

case class Folder(id: String, name: String, created_user: User, created_at: String, updated_user: User, updated_at: String)
object Folder {
  implicit def jsonWrites = Json.writes[Folder]
}

case class Element(`type`: String, id: String, name: String, created_user: User, created_at: String, updatedBy: User, updatedAt: String)
object Element {
  implicit def jsonWrites = Json.writes[Element]
}

case class Elements(current_folder: Folder, elements: Seq[Element])
object Elements {
  implicit def jsonWrites = Json.writes[Elements]
}