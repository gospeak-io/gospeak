package gospeak.core.domain.messages

import java.time.LocalDateTime

import gospeak.core.domain.Event
import gospeak.libs.scala.domain.{Mustache, Tag}

final case class MsgEvent(slug: Event.Slug,
                          name: Event.Name,
                          kind: Event.Kind,
                          start: LocalDateTime,
                          description: Mustache.Markdown[Message.EventInfo],
                          cfp: Option[MsgCfp.Embed],
                          venue: Option[MsgVenue.Embed],
                          proposals: Seq[MsgProposal.Embed],
                          tags: Seq[Tag],
                          published: Boolean,
                          links: Map[String, String],
                          publicLink: String,
                          orgaLink: String,
                          meetupLink: Option[String])

object MsgEvent {

  final case class Embed(id: Event.Id)

}
