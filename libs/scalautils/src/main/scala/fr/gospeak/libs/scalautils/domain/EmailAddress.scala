package fr.gospeak.libs.scalautils.domain

final class EmailAddress private(value: String) extends DataClass(value)

object EmailAddress {
  def from(in: String): Either[CustomException, EmailAddress] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(new EmailAddress(in))
    else Left(CustomException(s"'$in' is an invalid EmailAddress", errs))
  }

  // FIXME: improve
  private def errors(in: String): Seq[CustomError] =
    Seq(
      if (in.contains("@")) None else Some("Missing @")
    ).flatten.map(CustomError)

  final case class Contact(address: EmailAddress, name: Option[String]) {
    def format: String = name.map(n => s"$n<${address.value}>").getOrElse(address.value)
  }

  object Contact {
    def apply(email: EmailAddress): Contact = new Contact(email, None)
  }

}
