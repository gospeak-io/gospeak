package fr.gospeak.libs.scalautils

import scala.util.{Failure, Success, Try}

object Extensions {

  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toTry(e: => Throwable): Try[A] = in match {
      case Some(v) => Success(v)
      case None => Failure(e)
    }
  }

}
