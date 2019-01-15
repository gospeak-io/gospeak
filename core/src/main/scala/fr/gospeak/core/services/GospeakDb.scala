package fr.gospeak.core.services

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Page

import scala.concurrent.Future

trait GospeakDb {
  def getUser(): User // TODO mock auth, to remove

  def getGroupId(group: Group.Slug): Future[Option[Group.Id]]

  def getEventId(group: Group.Id, event: Event.Slug): Future[Option[Event.Id]]

  def getTalkId(talk: Talk.Slug): Future[Option[Talk.Id]]

  def getGroups(user: User.Id, params: Page.Params): Future[Page[Group]]

  def getGroup(id: Group.Id, user: User.Id): Future[Option[Group]]

  def getEvents(group: Group.Id, params: Page.Params): Future[Page[Event]]

  def getEvent(id: Event.Id): Future[Option[Event]]

  def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name, by: User.Id): Future[Event]

  def getTalks(user: User.Id, params: Page.Params): Future[Page[Talk]]

  def getTalk(id: Talk.Id, user: User.Id): Future[Option[Talk]]

  def createTalk(slug: Talk.Slug, title: Talk.Title, description: String, by: User.Id): Future[Talk]

  def getProposals(talk: Talk.Id, params: Page.Params): Future[Page[(Group, Proposal)]]

  def getProposals(group: Group.Id, params: Page.Params): Future[Page[Proposal]]

  def getProposal(id: Proposal.Id): Future[Option[Proposal]]
}
