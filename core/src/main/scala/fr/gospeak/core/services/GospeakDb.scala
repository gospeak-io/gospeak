package fr.gospeak.core.services

import fr.gospeak.core.domain._

import scala.concurrent.Future

trait GospeakDb {
  def getUser(): User // TODO mock auth, to remove

  def getGroupId(group: Group.Slug): Future[Option[Group.Id]]

  def getEventId(group: Group.Id, event: Event.Slug): Future[Option[Event.Id]]

  def getTalkId(talk: Talk.Slug): Future[Option[Talk.Id]]

  def getGroups(user: User.Id): Future[Seq[Group]]

  def getGroup(id: Group.Id, user: User.Id): Future[Option[Group]]

  def getEvents(group: Group.Id): Future[Seq[Event]]

  def getEvent(id: Event.Id): Future[Option[Event]]

  def createEvent(group: Group.Id, slug: Event.Slug, name: Event.Name): Future[Event]

  def getTalks(user: User.Id): Future[Seq[Talk]]

  def getTalk(id: Talk.Id, user: User.Id): Future[Option[Talk]]

  def getProposals(talk: Talk.Id): Future[Seq[(Group, Proposal)]]

  def getProposals(group: Group.Id): Future[Seq[Proposal]]

  def getProposal(id: Proposal.Id): Future[Option[Proposal]]
}
