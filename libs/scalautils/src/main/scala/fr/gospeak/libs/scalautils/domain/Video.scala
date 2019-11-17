package fr.gospeak.libs.scalautils.domain

final class Video private(value: Url) extends DataClass(value.value) {
  def url: Url = value
}

object Video {
  def from(in: String): Either[CustomException, Video] =
    Url.from(in).flatMap(from).left.map(_.copy(message = s"'$in' is an invalid Video"))

  def from(in: Url): Either[CustomException, Video] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new Video(in))
    else Left(CustomException(s"'$in' is an invalid Video", errs))
  }

  // FIXME: improve
  private def errors(in: Url): Seq[CustomError] =
    Seq(
      if (in.value.startsWith("http")) None else Some("Do not starts with 'http'")
    ).flatten.map(CustomError)
}
