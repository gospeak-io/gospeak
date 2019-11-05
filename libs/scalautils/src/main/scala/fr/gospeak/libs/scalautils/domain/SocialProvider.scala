package fr.gospeak.libs.scalautils.domain

import fr.gospeak.libs.scalautils.domain.Avatar.Source

// FIXME use sealerate
object SocialProvider {

  sealed trait SocialProvider {
    def toSource: Either[CustomException, Source] = Source.from(this.toString)
  }

  case object Google extends SocialProvider

  case object Twitter extends SocialProvider

  case object LinkedIn extends SocialProvider

  case object Facebook extends SocialProvider

  case object Github extends SocialProvider

  val all: Seq[SocialProvider] = Seq(Google, Twitter, LinkedIn, Facebook, Github)

  def from(in: String): Either[CustomException, SocialProvider] =
    all.find(_.toString.toLowerCase == in.toLowerCase())
      .map(Right(_))
      .getOrElse(Left(CustomException(s"'$in' is not a valid Social Provider")))


}
