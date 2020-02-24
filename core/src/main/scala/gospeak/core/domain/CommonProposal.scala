package gospeak.core.domain

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import gospeak.core.domain.utils.{Constants, Info}
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class CommonProposal(id: CommonProposal.Id,
                                external: Boolean,
                                talk: CommonProposal.EmbedTalk,
                                group: Option[CommonProposal.EmbedGroup],
                                cfp: Option[CommonProposal.EmbedCfp],
                                event: Option[CommonProposal.EmbedEvent],
                                eventExt: Option[CommonProposal.EmbedExtEvent],
                                title: Talk.Title,
                                status: Proposal.Status,
                                duration: FiniteDuration,
                                speakers: NonEmptyList[User.Id],
                                slides: Option[Slides],
                                video: Option[Video],
                                tags: Seq[Tag],
                                info: Info) {
  def users: List[User.Id] = (speakers.toList ++ info.users).distinct

  def date: Instant = event.map(_.start).orElse(eventExt.flatMap(_.start)).map(TimeUtils.toInstant(_, Constants.defaultZoneId)).getOrElse(info.createdAt)

  def logo: Option[Logo] = eventExt.flatMap(_.logo).orElse(group.flatMap(_.logo))

  def eventName: Option[Event.Name] = event.map(_.name).orElse(eventExt.map(_.name))

  def eventKind: Option[Event.Kind] = event.map(_.kind).orElse(eventExt.map(_.kind))

  def hasSpeaker(user: User.Id): Boolean = speakers.toList.contains(user)

  def hasOrga(user: User.Id): Boolean = group.exists(_.owners.toList.contains(user))
}

object CommonProposal {
  def apply(p: Proposal, t: Talk, g: Group, c: Cfp, eOpt: Option[Event]): CommonProposal =
    new CommonProposal(
      Id(p.id), external = false,
      EmbedTalk(t.id, t.slug, t.duration),
      Some(EmbedGroup(g.id, g.slug, g.name, g.logo, g.owners)),
      Some(EmbedCfp(c.id, c.slug, c.name)),
      eOpt.map(e => EmbedEvent(e.id, e.slug, e.name, e.kind, e.start)),
      None, p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, p.info)

  def apply(p: ExternalProposal, t: Talk, e: ExternalEvent): CommonProposal =
    new CommonProposal(Id(p.id), external = true, EmbedTalk(t.id, t.slug, t.duration), None, None, None, Some(EmbedExtEvent(e.id, e.name, e.kind, e.logo, e.start, e.url, p.url)), p.title, p.status, p.duration, p.speakers, p.slides, p.video, p.tags, p.info)

  final class Id private(value: String) extends DataClass(value) with IId {
    def internal: Proposal.Id = Proposal.Id.from(this)

    def external: ExternalProposal.Id = ExternalProposal.Id.from(this)
  }

  object Id extends UuidIdBuilder[Id]("CommonProposal.Id", new Id(_)) {
    def apply(id: Proposal.Id): Id = new Id(id.value)

    def apply(id: ExternalProposal.Id): Id = new Id(id.value)
  }

  final case class EmbedTalk(id: Talk.Id,
                             slug: Talk.Slug,
                             duration: FiniteDuration)

  final case class EmbedGroup(id: Group.Id,
                              slug: Group.Slug,
                              name: Group.Name,
                              logo: Option[Logo],
                              owners: NonEmptyList[User.Id])

  final case class EmbedCfp(id: Cfp.Id,
                            slug: Cfp.Slug,
                            name: Cfp.Name)

  final case class EmbedEvent(id: Event.Id,
                              slug: Event.Slug,
                              name: Event.Name,
                              kind: Event.Kind,
                              start: LocalDateTime)

  final case class EmbedExtEvent(id: ExternalEvent.Id,
                                 name: Event.Name,
                                 kind: Event.Kind,
                                 logo: Option[Logo],
                                 start: Option[LocalDateTime],
                                 url: Option[Url],
                                 proposalUrl: Option[Url])

}
