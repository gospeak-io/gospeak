package gospeak.core.domain

import java.time.{Instant, LocalDateTime}

import cats.data.NonEmptyList
import gospeak.core.domain.utils.{Constants, Info}
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain._

import scala.concurrent.duration.FiniteDuration

final case class CommonProposal(title: Talk.Title,
                                status: Proposal.Status,
                                duration: FiniteDuration,
                                speakers: NonEmptyList[User.Id],
                                slides: Option[SlidesUrl],
                                video: Option[VideoUrl],
                                tags: Seq[Tag],
                                talk: CommonProposal.EmbedTalk,
                                extra: Either[CommonProposal.External, CommonProposal.Internal],
                                info: Info) {
  def users: List[User.Id] = (speakers.toList ++ info.users).distinct

  def date: Instant = extra.fold(_.event.start, _.event.map(_.start)).map(TimeUtils.toInstant(_, Constants.defaultZoneId)).getOrElse(info.createdAt)

  def logo: Option[Logo] = extra.fold(_.event.logo, _.group.logo)

  def eventName: String = extra.fold(_.event.name.value, i => i.event.map(_.name.value).getOrElse(i.group.name.value))

  def eventKind: Option[Event.Kind] = extra.fold(e => Some(e.event.kind), _.event.map(_.kind))

  def hasSpeaker(user: User.Id): Boolean = speakers.toList.contains(user)

  def hasOrga(user: User.Id): Boolean = extra.exists(_.group.owners.toList.contains(user))

  def fold[A](f: CommonProposal.External => A)(g: CommonProposal.Internal => A): A = extra.fold(f, g)

  def internal: Option[CommonProposal.Internal] = extra.right.toOption

  def external: Option[CommonProposal.External] = extra.left.toOption
}

object CommonProposal {
  def apply(p: Proposal, t: Talk, g: Group, c: Cfp, eOpt: Option[Event]): CommonProposal =
    new CommonProposal(
      title = p.title,
      status = p.status,
      duration = p.duration,
      speakers = p.speakers,
      slides = p.slides,
      video = p.video,
      tags = p.tags,
      talk = EmbedTalk(t.id, t.slug, t.duration),
      extra = Right(Internal(
        id = p.id,
        group = InternalGroup(g.id, g.slug, g.name, g.logo, g.owners),
        cfp = InternalCfp(c.id, c.slug, c.name),
        event = eOpt.map(e => InternalEvent(e.id, e.slug, e.name, e.kind, e.start)))),
      info = p.info)

  def apply(p: ExternalProposal, t: Talk, e: ExternalEvent): CommonProposal =
    new CommonProposal(
      title = p.title,
      status = p.status,
      duration = p.duration,
      speakers = p.speakers,
      slides = p.slides,
      video = p.video,
      tags = p.tags,
      talk = EmbedTalk(t.id, t.slug, t.duration),
      extra = Left(External(p.id, ExternalExternalEvent(e.id, e.name, e.kind, e.logo, e.start, e.url, p.url))),
      info = p.info)

  final case class EmbedTalk(id: Talk.Id,
                             slug: Talk.Slug,
                             duration: FiniteDuration)

  final case class InternalGroup(id: Group.Id,
                                 slug: Group.Slug,
                                 name: Group.Name,
                                 logo: Option[Logo],
                                 owners: NonEmptyList[User.Id])

  final case class InternalCfp(id: Cfp.Id,
                               slug: Cfp.Slug,
                               name: Cfp.Name)

  final case class InternalEvent(id: Event.Id,
                                 slug: Event.Slug,
                                 name: Event.Name,
                                 kind: Event.Kind,
                                 start: LocalDateTime)

  final case class Internal(id: Proposal.Id,
                            group: InternalGroup,
                            cfp: InternalCfp,
                            event: Option[InternalEvent])

  final case class ExternalExternalEvent(id: ExternalEvent.Id,
                                         name: Event.Name,
                                         kind: Event.Kind,
                                         logo: Option[Logo],
                                         start: Option[LocalDateTime],
                                         url: Option[Url],
                                         proposalUrl: Option[Url])

  final case class External(id: ExternalProposal.Id,
                            event: ExternalExternalEvent)

}
