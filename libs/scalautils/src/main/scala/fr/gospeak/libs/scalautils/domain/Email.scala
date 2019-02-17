package fr.gospeak.libs.scalautils.domain

final class Email private(value: String) extends DataClass(value)

object Email {
  def from(in: String): Either[CustomException, Email] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new Email(in))
    else Left(CustomException(s"'$in' is an invalid Email", errs))
  }

  // FIXME: improve
  private def errors(in: String): Seq[CustomError] =
    Seq(
      if (in.contains("@")) None else Some("Missing @")
    ).flatten.map(CustomError)
}
