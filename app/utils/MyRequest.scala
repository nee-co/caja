package utils

import play.api.mvc.{Request, WrappedRequest}

abstract class MyRequest[A](request: Request[A]) extends WrappedRequest(request)

class InsideRequest[A] private[utils](request: Request[A], val loginId: Int) extends MyRequest(request)
