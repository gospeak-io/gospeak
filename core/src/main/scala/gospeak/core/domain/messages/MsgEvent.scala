package gospeak.core.domain.messages

import java.time.LocalDateTime

import gospeak.core.domain.Event
import gospeak.libs.scala.domain.{MustacheMarkdown, Tag}

final case class MsgEvent(slug: Event.Slug,
                          name: Event.Name,
                          kind: Event.Kind,
                          start: LocalDateTime,
                          description: MustacheMarkdown[Message.EventInfo],
                          cfp: Option[MsgCfp.Embed],
                          venue: Option[MsgVenue.Embed],
                          proposals: Seq[MsgProposal.Embed],
                          tags: Seq[Tag],
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
                         description: MustacheMarkdown[Message.EventInfo],
                         venue: Option[MsgVenue.Embed],
                         tags: Seq[Tag],
                         published: Boolean,
                         links: Map[String, String],
                         publicLink: String,
                         orgaLink: String,
                         meetupLink: Option[String])

}
