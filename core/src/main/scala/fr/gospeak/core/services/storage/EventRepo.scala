package fr.gospeak.core.services.storage

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

trait EventRepo extends OrgaEventRepo with SpeakerEventRepo with UserEventRepo with AuthEventRepo with PublicEventRepo with SuggestEventRepo

trait OrgaEventRepo {
  def create(data: Event.Data)(implicit ctx: OrgaCtx): IO[Event]

  def edit(event: Event.Slug, data: Event.Data)(implicit ctx: OrgaCtx): IO[Done]

  def editNotes(event: Event.Slug, notes: String)(implicit ctx: OrgaCtx): IO[Done]

  def attachCfp(event: Event.Slug, cfp: Cfp.Id)(implicit ctx: OrgaCtx): IO[Done]

  def editTalks(event: Event.Slug, talks: Seq[Proposal.Id])(implicit ctx: OrgaCtx): IO[Done]

  def publish(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Done]

  def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]]

  def list(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[Seq[Event]]

  def list(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Seq[(Event, Venue)]]

  def list(ids: Seq[Event.Id]): IO[Seq[Event]]

  def listAfter(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]]

  def find(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event]]

  def listRsvps(event: Event.Id): IO[Seq[Event.Rsvp]]

  def listRsvps(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): IO[Seq[Event.Rsvp]]
}

trait SpeakerEventRepo {
  def list(ids: Seq[Event.Id]): IO[Seq[Event]]
}

trait UserEventRepo {
  def listIncoming(params: Page.Params)(user: User.Id, now: Instant): IO[Page[(Event.Full, Option[Event.Rsvp])]]
}

trait AuthEventRepo

trait PublicEventRepo {
  def listPublished(group: Group.Id, params: Page.Params): IO[Page[Event.Full]]

  def findPublished(group: Group.Id, event: Event.Slug): IO[Option[Event.Full]]

  def countYesRsvp(event: Event.Id): IO[Long]

  def findRsvp(event: Event.Id, user: User.Id): IO[Option[Event.Rsvp]]

  def findFirstWait(event: Event.Id): IO[Option[Event.Rsvp]]

  def createRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done]

  def editRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done]

  def listRsvps(event: Event.Id): IO[Seq[Event.Rsvp]]
}

trait SuggestEventRepo {
  def list(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def listTags(): IO[Seq[Tag]]
}
