package fr.gospeak.core.services

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Email, Page}

import scala.concurrent.duration.FiniteDuration

trait GospeakDb {
  def createUser(firstName: String, lastName: String, email: Email): IO[User]

  def getUser(email: Email): IO[Option[User]]

  def getUsers(ids: NonEmptyList[User.Id]): IO[Seq[User]]

  def createGroup(slug: Group.Slug, name: Group.Name, description: String, by: User.Id): IO[Group]

  def getGroup(user: User.Id, slug: Group.Slug): IO[Option[Group]]

  def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]]

  def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): IO[Event]

  def getEvent(group: Group.Id, event: Event.Slug): IO[Option[Event]]

  def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def createCfp(slug: Cfp.Slug, name: Cfp.Name, description: String, group: Group.Id, by: User.Id): IO[Cfp]

  def getCfp(slug: Cfp.Slug): IO[Option[Cfp]]

  def getCfp(id: Cfp.Id): IO[Option[Cfp]]

  def getCfp(id: Group.Id): IO[Option[Cfp]]

  def getCfps(params: Page.Params): IO[Page[Cfp]]

  def createTalk(slug: Talk.Slug, title: Talk.Title, duration: FiniteDuration, description: String, by: User.Id): IO[Talk]

  def getTalk(user: User.Id, slug: Talk.Slug): IO[Option[Talk]]

  def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def createProposal(talk: Talk.Id, cfp: Cfp.Id, title: Talk.Title, description: String, by: User.Id): IO[Proposal]

  def getProposal(id: Proposal.Id): IO[Option[Proposal]]

  def getProposals(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]]

  def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]]
}
