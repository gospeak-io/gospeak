package gospeak.libs.scala.domain

final class VideoUrl private(value: Url) extends DataClass(value.value) {
  def url: Url = value
}

object VideoUrl {
  def from(in: String): Either[CustomException, VideoUrl] =
    Url.from(in).flatMap(from).left.map(_.copy(message = s"'$in' is an invalid VideoUrl"))

  def from(in: Url): Either[CustomException, VideoUrl] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new VideoUrl(in))
    else Left(CustomException(s"'$in' is an invalid VideoUrl", errs))
  }

  // FIXME: improve
  private def errors(in: Url): Seq[CustomError] =
    Seq(
      if (in.value.startsWith("http")) None else Some("Do not starts with 'http'")
    ).flatten.map(CustomError)
}
