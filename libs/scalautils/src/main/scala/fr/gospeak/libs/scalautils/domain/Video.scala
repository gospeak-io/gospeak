package fr.gospeak.libs.scalautils.domain

final class Video private(value: Url) extends DataClass(value.value)

object Video {
  def from(in: String): Either[CustomException, Video] =
    Url.from(in).flatMap { url =>
      val errs = errors(url)
      if (errs.isEmpty) Right(new Video(url))
      else Left(CustomException("", errs))
    }.left.map(_.copy(message = s"'$in' is an invalid Video"))

  // FIXME: improve
  private def errors(in: Url): Seq[CustomError] =
    Seq(
      if (in.value.startsWith("http")) None else Some("Do not starts with 'http'")
    ).flatten.map(CustomError)
}
