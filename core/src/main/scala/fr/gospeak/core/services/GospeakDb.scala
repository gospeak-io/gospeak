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

  def getGroupId(slug: Group.Slug): IO[Option[Group.Id]]

  def getEventId(group: Group.Id, slug: Event.Slug): IO[Option[Event.Id]]

  def getTalkId(user: User.Id, slug: Talk.Slug): IO[Option[Talk.Id]]

  def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]]

  def getGroup(id: Group.Id, user: User.Id): IO[Option[Group]]

  def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def getEvent(id: Event.Id): IO[Option[Event]]

  def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): IO[Event]

  def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def getTalk(id: Talk.Id, user: User.Id): IO[Option[Talk]]

  def createTalk(slug: Talk.Slug, title: Talk.Title, description: String, by: User.Id): IO[Talk]

  def getProposals(group: Group.Id, params: Page.Params): IO[Page[Proposal]]

  def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Group, Proposal)]]

  def getProposal(id: Proposal.Id): IO[Option[Proposal]]
}
