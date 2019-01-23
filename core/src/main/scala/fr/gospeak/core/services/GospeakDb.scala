package fr.gospeak.core.services

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.{Done, Email, Page}

trait GospeakDb {
  def login(user: User): IO[Done] // TODO mock auth, to remove
  def logout(): IO[Done] // TODO mock auth, to remove
  def userAware(): Option[User] // TODO mock auth, to remove
  def authed(): User // TODO mock auth, to remove

  def createUser(firstName: String, lastName: String, email: Email): IO[User]

  def getUser(email: Email): IO[Option[User]]

  def createGroup(slug: Group.Slug, name: Group.Name, description: String, by: User.Id): IO[Group]

  def getGroupId(slug: Group.Slug): IO[Option[Group.Id]]

  def getGroup(id: Group.Id, user: User.Id): IO[Option[Group]]

  def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]]

  def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): IO[Event]

  def getEventId(group: Group.Id, slug: Event.Slug): IO[Option[Event.Id]]

  def getEvent(id: Event.Id): IO[Option[Event]]

  def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def createCfp(slug: Cfp.Slug, name: Cfp.Name, description: String, group: Group.Id, by: User.Id): IO[Cfp]

  def getCfpId(slug: Cfp.Slug): IO[Option[Cfp.Id]]

  def getCfp(id: Group.Id): IO[Option[Cfp]]

  def getCfp(id: Cfp.Id): IO[Option[Cfp]]

  def getCfps(params: Page.Params): IO[Page[Cfp]]

  def createTalk(slug: Talk.Slug, title: Talk.Title, description: String, by: User.Id): IO[Talk]

  def getTalkId(user: User.Id, slug: Talk.Slug): IO[Option[Talk.Id]]

  def getTalk(id: Talk.Id, user: User.Id): IO[Option[Talk]]

  def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def createProposal(talk: Talk.Id, cfp: Cfp.Id, title: Talk.Title, description: String, by: User.Id): IO[Proposal]

  def getProposal(id: Proposal.Id): IO[Option[Proposal]]

  def getProposals(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]]

  def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]]
}
