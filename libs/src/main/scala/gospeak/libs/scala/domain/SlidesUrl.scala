package gospeak.libs.scala.domain

final class SlidesUrl private(value: Url) extends DataClass(value.value) {
  def url: Url = value
}

object SlidesUrl {
  def from(in: String): Either[CustomException, SlidesUrl] =
    Url.from(in).flatMap(from).left.map(_.copy(message = s"'$in' is an invalid SlidesUrl"))

  def from(in: Url): Either[CustomException, SlidesUrl] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new SlidesUrl(in))
    else Left(CustomException(s"'$in' is an invalid SlidesUrl", errs))
  }

  // FIXME: improve
  private def errors(in: Url): Seq[CustomError] =
    Seq(
      if (in.value.startsWith("http")) None else Some("Do not starts with 'http'")
    ).flatten.map(CustomError)
}
