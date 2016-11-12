package services

import java.sql.Timestamp
import javax.inject.Inject

import models.Tables.FoldersRow
import org.joda.time.LocalDateTime
import utils.DAO

class DBService @Inject()(dao: DAO) {
  private def nowTimestamp: Timestamp = new Timestamp(new LocalDateTime().toDateTime().getMillis)
  private def uuid: String = java.util.UUID.randomUUID.toString
  def hasTop(groupId: Int): Boolean = dao.findTops.count(_.groupId == groupId) == 1
  def addFolder(parentId: Int, groupId: Int, userId:Int, name: String): Boolean = dao.create(FoldersRow(uuid, parentId, groupId, name, userId, nowTimestamp, userId, nowTimestamp))
  def clean(groupId:Int): Boolean = dao.deleteByGroupId(groupId)
}
