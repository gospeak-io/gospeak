package gospeak.libs.slack.domain

final case class SlackError(ok: Boolean,
                            error: String,
                            needed: Option[String],
                            provided: Option[String])
