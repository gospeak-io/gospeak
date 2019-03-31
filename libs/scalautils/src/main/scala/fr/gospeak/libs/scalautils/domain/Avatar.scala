package fr.gospeak.libs.scalautils.domain

final case class Avatar(url: Url,
                        source: Avatar.Source)

object Avatar {

  sealed trait Source

  object Source {

    case object Gravatar extends Source

    val all: Seq[Source] = Seq(Gravatar)

    def from(in: String): Either[CustomException, Source] =
      all.find(_.toString == in).map(Right(_)).getOrElse(Left(CustomException(s"'$in' is not a valid Avatar.Source")))

  }

}
