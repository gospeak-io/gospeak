package gospeak.core.domain.messages

import java.time.LocalDateTime

import gospeak.core.domain.Event
import gospeak.libs.scala.domain.{LiquidMarkdown, Tag}

final case class MsgEvent(slug: Event.Slug,
                          name: Event.Name,
                          kind: Event.Kind,
                          start: LocalDateTime,
                          description: LiquidMarkdown[Message.EventInfo],
                          cfp: Option[MsgCfp.Embed],
                          venue: Option[MsgVenue.Embed],
                          proposals: List[MsgProposal.Embed],
                          tags: List[Tag],
                          published: Boolean,
                          links: Map[String, String],
                          publicLink: String,
                          orgaLink: String,
                          meetupLink: Option[String]) {
  def embed: MsgEvent.Embed = MsgEvent.Embed(slug, name, kind, start, description, venue, tags, published, links, publicLink, orgaLink, meetupLink)
}

object MsgEvent {

  final case class Embed(slug: Event.Slug,
                         name: Event.Name,
                         kind: Event.Kind,
                         start: LocalDateTime,
                         description: LiquidMarkdown[Message.EventInfo],
                         venue: Option[MsgVenue.Embed],
                         tags: List[Tag],
                         published: Boolean,
                         links: Map[String, String],
                         publicLink: String,
                         orgaLink: String,
                         meetupLink: Option[String])

}
