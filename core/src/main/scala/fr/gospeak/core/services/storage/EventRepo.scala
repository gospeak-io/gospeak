package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

trait EventRepo extends OrgaEventRepo with SpeakerEventRepo with UserEventRepo with AuthEventRepo with PublicEventRepo with SuggestEventRepo

trait OrgaEventRepo {
  def create(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event]

  def edit(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done]

  def editNotes(group: Group.Id, event: Event.Slug)(notes: Option[String], by: User.Id, now: Instant): IO[Done]

  def attachCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): IO[Done]

  def editTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done]

  def publish(group: Group.Id, event: Event.Slug, by: User.Id, now: Instant): IO[Done]

  def list(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def list(group: Group.Id, venue: Venue.Id): IO[Seq[Event]]

  def list(group: Group.Id, partner: Partner.Id): IO[Seq[(Event, Venue)]]

  def list(ids: Seq[Event.Id]): IO[Seq[Event]]

  def listAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]]

  def find(group: Group.Id, event: Event.Slug): IO[Option[Event]]
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
}

trait SuggestEventRepo {
  def list(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def listTags(): IO[Seq[Tag]]
}
