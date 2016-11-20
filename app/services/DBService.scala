package services

import java.sql.Timestamp
import java.text.SimpleDateFormat
import javax.inject.Inject

import models.ObjectProperty
import models.Tables.{FilesRow, FoldersRow}
import org.joda.time.{DateTime, LocalDateTime}
import utils.DAO

class DBService @Inject()(dao: DAO) {
  private def nowTimestamp: Timestamp = new Timestamp(new LocalDateTime().toDateTime().getMillis)
  private def DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  def uuid: String = java.util.UUID.randomUUID.toString
  def canReadFile(fileId: Option[String], groups: Seq[String]): Boolean = fileId.fold(false)(id => groups.contains(dao.findFile(id).fold("")(_.groupId)))
  def canCreateAndRead(folderId: Option[String], groups: Seq[String]): Boolean = folderId.fold(false)(id => groups.contains(dao.findFolder(id).fold("")(_.groupId)))
  def canUpdateAndDeleteFile(id: String, userId: Option[Int]): Boolean = userId.fold(return false)(identity) == dao.findFile(id).fold(return false)(_.insertedBy)
  def canUpdateAndDeleteFolder(id: String, userId: Option[Int]): Boolean = userId.fold(return false)(identity) == dao.findFolder(id).fold(return false)(_.insertedBy)
  def hasTop(groupId: String): Boolean = dao.findFolders("0").count(_.groupId == groupId) != 0
  def hasIdenticalName(id: Option[String], newObjectName: Option[String]): Boolean = dao.hasObjectByName(id.fold("")(identity), newObjectName.fold("")(identity))
  def myTops(groupIds: Seq[String]): Seq[FoldersRow] = dao.findFolders("0").filter(folder => groupIds.contains(folder.groupId))
  def clean(groupId: String): Boolean = dao.deleteByGroupId(groupId)

  def createFile(id:String, parentId: String, userId: Int, name: String, objectKey: String): Option[String] = {
    dao.findFolder(parentId) match {
      case Some(parent) =>
        if (dao.create(FilesRow(id, parent.id, parent.groupId, name, objectKey, userId, nowTimestamp, userId, nowTimestamp))) {
          updateFolders(parent.id, userId)
          Some(id)
        } else None
      case None => None
    }
  }

  def updateFile(file: Option[ObjectProperty], userId: Int, name: String): Option[String] = {
    file.map { file =>
      if (!dao.hasObjectByName(file.parentId, name) && name != "" && dao.update(FilesRow(file.id, file.parentId, file.groupId, name, s"${file.parentId}/${file.id}", file.insertedBy, new Timestamp(DateFormat.parse(file.insertedAt).getTime), userId, nowTimestamp))) {
        updateFolders(file.parentId, userId)
        Some(file.id)
      } else None
    }.getOrElse(None)
  }

  def createFolder(parentId: String, groupId: String, userId: Int, name: String): Option[String] = {
    val id = uuid
    dao.findFolder(parentId) match {
      case Some(parent) =>
        if (dao.create(FoldersRow(id, parent.id, parent.groupId, name, userId, nowTimestamp, userId, nowTimestamp))) {
          updateFolders(parent.id, userId)
          Some(id)
        } else None
      case None => if (dao.create(FoldersRow(id, parentId, groupId, name, userId, nowTimestamp, userId, nowTimestamp))) Some(id) else None
    }
  }

  def updateFolder(folder: Option[ObjectProperty], userId: Int, name: String): Option[String] = {
    folder.map { folder =>
      if (!dao.hasObjectByName(folder.parentId, name) && name != "" && dao.update(FoldersRow(folder.id, folder.parentId, folder.groupId, name, folder.insertedBy, new Timestamp(DateFormat.parse(folder.insertedAt).getTime), userId, nowTimestamp))) {
        updateFolders(folder.parentId, userId)
        Some(folder.id)
      } else None
    }.getOrElse(None)
  }

  def updateFolders(folderId: String, userId: Int): Unit = {
    dao.findFolder(folderId).fold()(row => {
      dao.update(FoldersRow(row.id, row.parentId, row.groupId, row.name, row.insertedBy, row.insertedAt, userId, nowTimestamp))
      updateFolders(row.parentId, userId)
    })
  }

  def deleteFile(fileId: String): Boolean = dao.deleteFileById(fileId)

//  def deleteUnderElements(folderId: String): Unit = {
//    val underFileIds = dao.findFiles(folderId)
//    val underFolderIds = dao.findFolders(folderId).map(_.id)
//
//    dao.findFolder(folderId).fold()(dao.delete(_))
//    underFileIds.foreach(dao.delete)
//    underFolderIds.foreach(deleteUnderElements)
//  }

  def getFile(id: String): Option[ObjectProperty] = dao.findFile(id).map(file => ObjectProperty("file", file.id,file.parentId, file.name, file.groupId, file.insertedBy, new DateTime(file.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), file.updatedBy, new DateTime(file.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))
  def getFolder(id: String): Option[ObjectProperty] = dao.findFolder(id).map(folder => ObjectProperty("folder", folder.id, folder.parentId, folder.name, folder.groupId, folder.insertedBy, new DateTime(folder.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), folder.updatedBy, new DateTime(folder.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))

  def getUnderElements(id: String): Seq[ObjectProperty] = {
    val files = dao.findFiles(id).map(file => ObjectProperty("file", file.id, file.parentId, file.name, file.groupId,file.insertedBy, new DateTime(file.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), file.updatedBy, new DateTime(file.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))
    val folders = dao.findFolders(id).map(folder => ObjectProperty("folder", folder.id, folder.parentId, folder.name, folder.groupId, folder.insertedBy, new DateTime(folder.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), folder.updatedBy, new DateTime(folder.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))

    files ++ folders
  }
}
