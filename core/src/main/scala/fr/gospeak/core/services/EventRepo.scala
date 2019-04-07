package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Event, Group, Proposal, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait EventRepo {
  def create(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event]

  def edit(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done]

  def editTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done]

  def find(group: Group.Id, event: Event.Slug): IO[Option[Event]]

  def list(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def list(ids: Seq[Event.Id]): IO[Seq[Event]]

  def listAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]]
}
