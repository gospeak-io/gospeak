package fr.gospeak.infra.services.slack.api

final case class SlackMessage(`type`: String,
                              subtype: String,
                              text: String,
                              ts: String,
                              username: String,
                              bot_id: String,
                              t: Option[Double])

object SlackMessage {

  final case class Posted(message: SlackMessage,
                          channel: SlackChannel.Id,
                          ts: String,
                          ok: Boolean)

}
