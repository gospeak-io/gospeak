package gospeak.libs.meetup.domain

final case class MeetupError(error: String, error_description: Option[String]) {
  def format: String = error_description.map(description => s"$description ($error)").getOrElse(error)
}

object MeetupError {

  final case class NotAuthorized(code: String, problem: String, details: String) {
    def toErr: MeetupError = MeetupError(code, Some(problem))
  }

  final case class Code(code: String, message: String) {
    def toErr: MeetupError = MeetupError(code, Some(message))
  }

  final case class Multi(errors: List[Code]) {
    def toErr: MeetupError = errors.headOption.map(_.toErr).getOrElse(MeetupError("empty_error_list", Some("List of errors is empty")))
  }

}
