package utils

import models.Tables.{Files, FilesRow, Folders, FoldersRow}
import javax.inject.Inject

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import slick.lifted.TableQuery
import slick.driver.MySQLDriver.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class DAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {
  val files   = new TableQuery(tag => new Files(tag))
  val folders = new TableQuery(tag => new Folders(tag))

  def create[T](obj: T): Boolean = {
    val result = obj match {
      case obj: FilesRow => db.run(files += obj)
      case obj: FoldersRow => db.run(folders += obj)
      case other => return false
    }

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(addResult) => true
      case Failure(t) => false
    }
  }

  def update[T](obj: T): Boolean = {
    val result = obj match {
      case obj: FilesRow => db.run(files.filter(_.id === obj.id).update(obj))
      case obj: FoldersRow => db.run(folders.filter(_.id === obj.id).update(obj))
      case other => return false
    }

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(updateResult) => updateResult == 1
      case Failure(t) => false
    }
  }

  def delete[T](obj: T): Boolean = {
    val result = obj match {
      case obj: FilesRow => db.run(files.filter(_.id === obj.id).delete)
      case obj: FoldersRow => db.run(folders.filter(_.id === obj.id).delete)
      case other => return false
    }

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(deleteResult) => deleteResult == 1
      case Failure(t) => false
    }
  }

  def deleteFileById(id: String): Boolean = {
    val result = db.run(files.filter(_.id === id).delete)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(deleteResult) => deleteResult == 1
      case Failure(t) => false
    }
  }

  def deleteFolderById(id: String): Boolean = {
    val result = db.run(folders.filter(_.id === id).delete)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(deleteResult) => deleteResult == 1
      case Failure(t) => false
    }
  }

  def deleteByGroupId(groupId: String): Boolean = {
    val filesResult = db.run(files.filter(_.groupId === groupId).delete)
    val foldersResult = db.run(folders.filter(_.groupId === groupId).delete)

    Await.ready(filesResult, Duration.Inf)
    Await.ready(foldersResult, Duration.Inf)

    (filesResult.value.get, foldersResult.value.get) match {
      case (Success(r1), Success(r2)) => true
      case (_, _) => false
    }
  }

  def findFile(id: String): Option[FilesRow] = {
    val result = db.run(files.filter(_.id === id).result.headOption)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(file) => if (file.nonEmpty) file else None
      case Failure(t) => None
    }
  }

  def findFiles(parentId: String): Seq[FilesRow] = {
    val result = db.run(files.filter(_.parentId === parentId).result)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(objects) => objects
      case Failure(t) => Seq.empty[FilesRow]
    }
  }

  def findFolder(id: String): Option[FoldersRow] = {
    val result = db.run(folders.filter(_.id === id).result.headOption)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(folder) => if (folder.nonEmpty) folder else None
      case Failure(t) => None
    }
  }

  def findFolders(parentId: String): Seq[FoldersRow] = {
    val result = db.run(folders.filter(_.parentId === parentId).result)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(objects) => objects
      case Failure(t) => Seq.empty[FoldersRow]
    }
  }

  def hasObjectByName(folderId: String, name: String): Boolean = {
    val fileSizeResult = db.run(files.filter(_.parentId === folderId).filter(_.name === name).size.result)
    val folderSizeResult = db.run(folders.filter(_.parentId === folderId).filter(_.name === name).size.result)

    Await.ready(fileSizeResult,   Duration.Inf)
    Await.ready(folderSizeResult, Duration.Inf)

    (fileSizeResult.value.get, folderSizeResult.value.get) match {
      case (Success(fileSize), Success(folderSize)) => fileSize + folderSize != 0
      case (_, _) => false
    }
  }
}
