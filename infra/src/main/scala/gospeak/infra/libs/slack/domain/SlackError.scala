package gospeak.infra.libs.slack.domain

final case class SlackError(ok: Boolean,
                            error: String,
                            needed: Option[String],
                            provided: Option[String])
