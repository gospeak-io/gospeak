package fr.gospeak.core.services

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, Email, Markdown, Page}

trait GospeakDb {
  def createUser(slug: User.Slug, firstName: String, lastName: String, email: Email, now: Instant): IO[User]

  def getUser(email: Email): IO[Option[User]]

  def getUser(slug: User.Slug): IO[Option[User]]

  def getUsers(ids: Seq[User.Id]): IO[Seq[User]]

  def createGroup(data: Group.Data, by: User.Id, now: Instant): IO[Group]

  def getGroup(user: User.Id, slug: Group.Slug): IO[Option[Group]]

  def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]]

  def createEvent(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event]

  def getEvent(group: Group.Id, event: Event.Slug): IO[Option[Event]]

  def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def updateEvent(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done]

  def getEventsAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]]

  def createCfp(data: Cfp.Data, group: Group.Id, by: User.Id, now: Instant): IO[Cfp]

  def getCfp(slug: Cfp.Slug): IO[Option[Cfp]]

  def getCfp(id: Cfp.Id): IO[Option[Cfp]]

  def getCfp(id: Group.Id): IO[Option[Cfp]]

  def getCfpAvailables(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]]

  def createTalk(data: Talk.Data, by: User.Id, now: Instant): IO[Talk]

  def getTalk(user: User.Id, slug: Talk.Slug): IO[Option[Talk]]

  def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def getTalks(ids: Seq[Talk.Id]): IO[Seq[Talk]]

  def updateTalk(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done]

  def updateTalkStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done]

  def createProposal(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal]

  def getProposal(id: Proposal.Id): IO[Option[Proposal]]

  def getProposal(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]]

  def getProposals(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]]

  def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]]

  def getProposals(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]
}
