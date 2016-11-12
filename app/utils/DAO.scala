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

  def findTops: Seq[FoldersRow] = {
    val result = db.run(folders.filter(_.name === "top").result)

    Await.ready(result, Duration.Inf)

    result.value.get match {
      case Success(tops) => tops
      case Failure(t) => Seq.empty[FoldersRow]
    }
  }

  def deleteByGroupId(groupId: Int): Boolean = {
    val filesResult = db.run(files.filter(_.groupId === groupId).delete)
    val foldersResult = db.run(folders.filter(_.groupId === groupId).delete)

    Await.ready(filesResult, Duration.Inf)
    Await.ready(foldersResult, Duration.Inf)

    (filesResult.value.get, foldersResult.value.get) match {
      case (Success(result1), Success(result2)) => true
      case (_, _) => false
    }
  }
}
