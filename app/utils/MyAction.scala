package utils

import play.api.mvc.{ActionBuilder, Request, Result, Results}

import scala.concurrent.Future

object MyAction {
  def inside = new InsideAction
}

class InsideAction extends ActionBuilder[InsideRequest]{
  override def invokeBlock[A](request: Request[A], block: (InsideRequest[A]) => Future[Result]): Future[Result] = {
    request.headers.get("x-consumer-custom-id").map(_.toInt) match {
      case Some(id) => block(new InsideRequest(request, id))
      case None => Future.successful(Results.Unauthorized)
    }
  }
}
