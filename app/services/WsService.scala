package services

import javax.inject.Inject

import models.{Group, Groups, User, Users}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class WsService @Inject()(ws: WSClient) {
  def user(id: Int): Option[User] = {
    val user: Future[User] = ws.url(s"${sys.env("CUENTA_URL")}/internal/users/$id").get.map(_.json.validate[User].get)

    Await.ready(user, Duration.Inf)

    user.value.get match {
      case Success(result) => Some(result)
      case Failure(t) => None
    }
  }

  def users(ids: Seq[Int]): Seq[User] = {
    val userIds = ids.map(_.toString).mkString("+")
    val users: Future[Users] = ws.url(s"${sys.env("CUENTA_URL")}/internal/users/list?user_ids=$userIds").get.map(_.json.validate[Users].get)

    Await.ready(users, Duration.Inf)

    users.value.get match {
      case Success(result) => result.users
      case Failure(t) => Seq.empty[User]
    }
  }

  def groups(userId: Int): Seq[Group] = {
    val groups: Future[Groups] = ws.url(s"${sys.env("CADENA_URL")}/internal/groups?user_id=$userId").get.map(_.json.validate[Groups].get)

    Await.ready(groups, Duration.Inf)

    groups.value.get match {
      case Success(result) => result.groups
      case Failure(t) => Seq.empty[Group]
    }
  }
}
