package services

import java.sql.Timestamp
import java.text.SimpleDateFormat
import javax.inject.Inject

import models.ObjectProperty
import models.Tables.{FilesRow, FoldersRow}
import org.joda.time.{DateTime, LocalDateTime}
import utils.DAO

class DBService @Inject()(dao: DAO, s3: S3Service) {
  private def now: Timestamp = new Timestamp(new LocalDateTime().toDateTime().getMillis)
  private def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  def uuid: String = java.util.UUID.randomUUID.toString
  def canReadFile(fileId: String, groups: Seq[String]): Boolean = groups.contains(dao.findFile(fileId).fold("")(_.groupId))
  def canCreateAndRead(folderId: String, groups: Seq[String]): Boolean = groups.contains(dao.findFolder(folderId).fold("")(_.groupId))
  def canUpdateAndDeleteFile(id: String, userId: Int): Boolean = userId == dao.findFile(id).fold(return false)(_.insertedBy)
  def canUpdateAndDeleteFolder(id: String, userId: Int): Boolean = userId == dao.findFolder(id).fold(return false)(_.insertedBy)
  def getTop(groupId: String): Option[FoldersRow] = dao.findFolders("0").find(_.groupId == groupId)
  def hasTop(groupId: String): Boolean = dao.findFolders("0").count(_.groupId == groupId) != 0
  def isUsableName(id: String, newObjectName: String): Boolean = !dao.hasObjectByName(id, newObjectName) && newObjectName != ""
  def myTops(groupIds: Seq[String]): Seq[FoldersRow] = dao.findFolders("0").filter(folder => groupIds.contains(folder.groupId))

  def clean(groupId: String): Boolean = {
    val deletedFileIds = dao.deleteByGroupId(groupId)
    deletedFileIds.foreach(s3.delete)
    !hasTop(groupId)
  }

  def createFile(id: String, parentId: String, userId: Int, name: String, size: Int): Option[String] = {
    dao.findFolder(parentId) match {
      case Some(parent) =>
        if (dao.create(FilesRow(id, parent.id, parent.groupId, name, userId, now, userId, now, size))) {
          updateFolders(parent.id, userId)
          Some(id)
        } else {
          None
        }
      case None => None
    }
  }

  def updateFile(file: ObjectProperty, userId: Int, name: String): Option[String] = {
    if (!dao.hasObjectByName(file.parentId, name) &&
        dao.update(FilesRow(file.id, file.parentId, file.groupId, name, file.insertedBy, new Timestamp(dateFormat.parse(file.insertedAt).getTime), userId, now, file.size.getOrElse(0)))) {
      updateFolders(file.parentId, userId)
      Some(file.id)
    } else {
      None
    }
  }

  def createFolder(parentId: String, groupId: String, userId: Int, name: String): Option[String] = {
    val id = uuid
    dao.findFolder(parentId) match {
      case Some(parent) =>
        if (dao.create(FoldersRow(id, parent.id, parent.groupId, name, userId, now, userId, now))) {
          updateFolders(parent.id, userId)
          Some(id)
        } else {
          None
        }
      case None => if (dao.create(FoldersRow(id, parentId, groupId, name, userId, now, userId, now))) Some(id) else None
    }
  }

  def updateFolder(folder: ObjectProperty, userId: Int, name: String): Option[String] = {
    if (!dao.hasObjectByName(folder.parentId, name) &&
        dao.update(FoldersRow(folder.id, folder.parentId, folder.groupId, name, folder.insertedBy, new Timestamp(dateFormat.parse(folder.insertedAt).getTime), userId, now))) {
      updateFolders(folder.parentId, userId)
      Some(folder.id)
    } else {
      None
    }
  }

  def updateFolders(folderId: String, userId: Int): Unit = {
    dao.findFolder(folderId).fold()(row => {
      dao.update(FoldersRow(row.id, row.parentId, row.groupId, row.name, row.insertedBy, row.insertedAt, userId, now))
      updateFolders(row.parentId, userId)
    })
  }

  def deleteFile(fileId: String, userId: Int): Boolean = {
    val file = getFile(fileId)

    if (dao.deleteFileById(fileId)) {
      updateFolders(file.fold("")(_.parentId), userId)
      true
    } else false
  }

  def deleteUnderElements(folderId: String): Unit = {
    val underFileIds = dao.findFiles(folderId)
    val underFolderIds = dao.findFolders(folderId).map(_.id)

    dao.findFolder(folderId).fold()(dao.delete(_))
    underFileIds.foreach(file => { s3.delete(file.id); dao.delete(file) })
    underFolderIds.foreach(deleteUnderElements)
  }

  def getFile(id: String): Option[ObjectProperty] = {
    dao.findFile(id).map(file => ObjectProperty("file", file.id,file.parentId, file.name, file.groupId, file.insertedBy, new DateTime(file.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), file.updatedBy, new DateTime(file.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), Some(file.size)))
  }

  def getFolder(id: String): Option[ObjectProperty] = {
    dao.findFolder(id).map(folder => ObjectProperty("folder", folder.id, folder.parentId, folder.name, folder.groupId, folder.insertedBy, new DateTime(folder.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), folder.updatedBy, new DateTime(folder.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), Option.empty[Int]))
  }

  def getUnderElements(id: String): Seq[ObjectProperty] = {
    val files = dao.findFiles(id).map(file => ObjectProperty("file", file.id, file.parentId, file.name, file.groupId,file.insertedBy, new DateTime(file.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), file.updatedBy, new DateTime(file.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), Some(file.size)))
    val folders = dao.findFolders(id).map(folder => ObjectProperty("folder", folder.id, folder.parentId, folder.name, folder.groupId, folder.insertedBy, new DateTime(folder.insertedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), folder.updatedBy, new DateTime(folder.updatedAt).toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"), Option.empty[Int]))

    folders ++ files
  }

  def getParents(folderId: String): Seq[FoldersRow] = {
    dao.findFolder(folderId).fold(Seq.empty[FoldersRow])(folder => folder +: getParents(folder.parentId))
  }
}
