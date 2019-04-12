package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Cfp, Event, Group, Proposal, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page}

// TODO: take full object as parameter instead of Id/slug to guarantee it exists
// TODO: remove list(Seq[Id]) methods as they are dangerous (right wise) and pack them with previous one (listProposalsWithSpeakers)
trait EventRepo extends OrgaEventRepo with SpeakerEventRepo with UserEventRepo with AuthEventRepo

trait OrgaEventRepo {
  def create(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event]

  def edit(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done]

  def attachCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): IO[Done]

  def editTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done]

  def list(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def list(ids: Seq[Event.Id]): IO[Seq[Event]]

  def listAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]]

  def find(group: Group.Id, event: Event.Slug): IO[Option[Event]]
}

trait SpeakerEventRepo {
  def list(ids: Seq[Event.Id]): IO[Seq[Event]]
}

trait UserEventRepo

trait AuthEventRepo
