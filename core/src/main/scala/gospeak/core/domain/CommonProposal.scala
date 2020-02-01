package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain.Tag

import scala.concurrent.duration.FiniteDuration

final case class CommonProposal(id: String,
                                external: Boolean,
                                talk: CommonProposal.EmbedTalk,
                                cfp: Option[CommonProposal.EmbedCfp],
                                event: Option[CommonProposal.EmbedEvent],
                                eventExt: Option[CommonProposal.EmbedExtEvent],
                                title: Talk.Title,
                                status: Option[Proposal.Status],
                                duration: FiniteDuration,
                                tags: Seq[Tag],
                                info: Info)

object CommonProposal {
  def apply(p: Proposal, t: Talk, c: Cfp, eOpt: Option[Event]): CommonProposal =
    new CommonProposal(p.id.value, external = false, EmbedTalk(t.id, t.slug, t.duration), Some(EmbedCfp(c.id, c.slug, c.name)), eOpt.map(e => EmbedEvent(e.id, e.slug, e.name, e.start)), None, p.title, Some(p.status), p.duration, p.tags, p.info)

  def apply(p: ExternalProposal, t: Talk, e: ExternalEvent): CommonProposal =
    new CommonProposal(p.id.value, external = true, EmbedTalk(t.id, t.slug, t.duration), None, None, Some(EmbedExtEvent(e.id, e.name, e.start)), p.title, None, p.duration, p.tags, p.info)

  final case class EmbedTalk(id: Talk.Id,
                             slug: Talk.Slug,
                             duration: FiniteDuration)

  final case class EmbedCfp(id: Cfp.Id,
                            slug: Cfp.Slug,
                            name: Cfp.Name)

  final case class EmbedEvent(id: Event.Id,
                              slug: Event.Slug,
                              name: Event.Name,
                              start: LocalDateTime)

  final case class EmbedExtEvent(id: ExternalEvent.Id,
                                 name: ExternalEvent.Name,
                                 start: LocalDateTime)

}
