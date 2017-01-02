package models

case class ObjectProperty(`type`: String, id: String, parentId: String, name: String, groupId: String, insertedBy: Int, insertedAt: String, updatedBy: Int, updatedAt: String, size: Option[Int]) {
  override def canEqual(other: Any): Boolean = other.isInstanceOf[ObjectProperty]
  override def hashCode: Int = (name.hashCode + 31) * 31 + insertedAt.hashCode
  override def equals(other: Any) = other match {
    case that: ObjectProperty =>
      that.canEqual(ObjectProperty.this) && `type` == that.`type` &&
      id == that.id && parentId == that.parentId && groupId == that.groupId &&
      name == that.name && insertedBy == that.insertedBy && insertedAt == that.insertedAt
      updatedBy == that.updatedBy && updatedAt == that.updatedAt && size == that.size
    case _ => false
  }
}
