package fr.gospeak.libs.scalautils.domain

final class Slides private(value: Url) extends DataClass(value.value)

object Slides {
  def from(in: String): Either[CustomException, Slides] =
    Url.from(in).flatMap(from).left.map(_.copy(message = s"'$in' is an invalid Slides"))

  def from(in: Url): Either[CustomException, Slides] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new Slides(in))
    else Left(CustomException(s"'$in' is an invalid Slides", errs))
  }

  // FIXME: improve
  private def errors(in: Url): Seq[CustomError] =
    Seq(
      if (in.value.startsWith("http")) None else Some("Do not starts with 'http'")
    ).flatten.map(CustomError)
}
