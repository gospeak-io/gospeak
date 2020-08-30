package gospeak.core.services.storage

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.{AdminCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.libs.scala.domain.{Done, LiquidMarkdown, Page, Tag}

trait EventRepo extends OrgaEventRepo with SpeakerEventRepo with UserEventRepo with AuthEventRepo with PublicEventRepo with AdminEventRepo with SuggestEventRepo

trait OrgaEventRepo {
  def create(data: Event.Data)(implicit ctx: OrgaCtx): IO[Event]

  def edit(event: Event.Slug, data: Event.Data)(implicit ctx: OrgaCtx): IO[Done]

  def editNotes(event: Event.Slug, notes: String)(implicit ctx: OrgaCtx): IO[Done]

  def attachCfp(event: Event.Slug, cfp: Cfp.Id)(implicit ctx: OrgaCtx): IO[Done]

  def editTalks(event: Event.Slug, talks: List[Proposal.Id])(implicit ctx: OrgaCtx): IO[Done]

  def publish(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Done]

  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]]

  def list(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[List[Event]]

  def list(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[List[(Event, Venue)]]

  def list(ids: List[Event.Id]): IO[List[Event]]

  def listAfter(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]]

  def find(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event]]

  def findFull(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event.Full]]

  def listRsvps(event: Event.Id): IO[List[Event.Rsvp]]

  def listRsvps(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): IO[List[Event.Rsvp]]
}

trait SpeakerEventRepo {
  def list(ids: List[Event.Id]): IO[List[Event]]
}

trait UserEventRepo {
  def listIncoming(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Event.Full, Option[Event.Rsvp])]]
}

trait AuthEventRepo

trait PublicEventRepo {
  def listAllPublishedSlugs()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Event.Slug)]]

  def listPublished(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Event.Full]]

  def findPublished(group: Group.Id, event: Event.Slug): IO[Option[Event.Full]]

  def findFull(group: Group.Slug, event: Event.Slug)(implicit ctx: UserAwareCtx): IO[Option[Event.Full]]

  def countYesRsvp(event: Event.Id): IO[Long]

  def findRsvp(event: Event.Id, user: User.Id): IO[Option[Event.Rsvp]]

  def findFirstWait(event: Event.Id): IO[Option[Event.Rsvp]]

  def createRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done]

  def editRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done]

  def listRsvps(event: Event.Id): IO[List[Event.Rsvp]]
}

trait AdminEventRepo {
  def listAllFromGroups(groups: List[Group.Id])(implicit ctx: AdminCtx): IO[List[Event]]

  def find(event: Event.Id)(implicit ctx: AdminCtx): IO[Option[Event]]

  def editDescription(event: Event.Id, description: LiquidMarkdown[Message.EventInfo])(implicit ctx: AdminCtx): IO[Done]
}

trait SuggestEventRepo {
  def listTags(): IO[List[Tag]]

  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]]
}
