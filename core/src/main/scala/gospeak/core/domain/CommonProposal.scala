package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain.{DataClass, IId, Tag, UuidIdBuilder}

import scala.concurrent.duration.FiniteDuration

final case class CommonProposal(id: CommonProposal.Id,
                                external: Boolean,
                                talk: CommonProposal.EmbedTalk,
                                cfp: Option[CommonProposal.EmbedCfp],
                                event: Option[CommonProposal.EmbedEvent],
                                eventExt: Option[CommonProposal.EmbedExtEvent],
                                title: Talk.Title,
                                status: Proposal.Status,
                                duration: FiniteDuration,
                                tags: Seq[Tag],
                                info: Info)

object CommonProposal {
  def apply(p: Proposal, t: Talk, c: Cfp, eOpt: Option[Event]): CommonProposal =
    new CommonProposal(Id(p.id), external = false, EmbedTalk(t.id, t.slug, t.duration), Some(EmbedCfp(c.id, c.slug, c.name)), eOpt.map(e => EmbedEvent(e.id, e.slug, e.name, e.start)), None, p.title, p.status, p.duration, p.tags, p.info)

  def apply(p: ExternalProposal, t: Talk, e: ExternalEvent): CommonProposal =
    new CommonProposal(Id(p.id), external = true, EmbedTalk(t.id, t.slug, t.duration), None, None, Some(EmbedExtEvent(e.id, e.name, e.start)), p.title, p.status, p.duration, p.tags, p.info)

  final class Id private(value: String) extends DataClass(value) with IId {
    def external: ExternalProposal.Id = ExternalProposal.Id.from(this)
  }

  object Id extends UuidIdBuilder[Id]("CommonProposal.Id", new Id(_)) {
    def apply(id: Proposal.Id): Id = new Id(id.value)

    def apply(id: ExternalProposal.Id): Id = new Id(id.value)
  }

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
                                 start: Option[LocalDateTime])

}
