package fr.gospeak.libs.scalautils.domain

final case class Avatar(url: Url,
                        source: Avatar.Source)

object Avatar {

  sealed trait Source

  object Source {

    case object Gravatar extends Source

    case object Twitter extends Source

    case object Google extends Source

    case object LinkedIn extends Source

    case object Meetup extends Source

    case object UserDefined extends Source

    val all: Seq[Source] = Seq(Gravatar, Twitter, LinkedIn, Meetup, Google, UserDefined)

    def from(in: String): Either[CustomException, Source] =
      all.find(_.toString == in).map(Right(_)).getOrElse(Left(CustomException(s"'$in' is not a valid Avatar.Source")))

  }

}
