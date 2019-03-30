package fr.gospeak.core.services

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain._

trait GospeakDb {
  def createUser(slug: User.Slug, firstName: String, lastName: String, email: Email, now: Instant): IO[User]

  def updateUser(user: User, now: Instant): IO[User]

  def createLoginRef(login: User.Login, user: User.Id): IO[Done]

  def createCredentials(credentials: User.Credentials): IO[User.Credentials]

  def updateCredentials(login: User.Login)(pass: User.Password): IO[Done]

  def deleteCredentials(login: User.Login): IO[Done]

  def getCredentials(login: User.Login): IO[Option[User.Credentials]]

  def getUser(login: User.Login): IO[Option[User]]

  def getUser(credentials: User.Credentials): IO[Option[User]]

  def getUser(email: Email): IO[Option[User]]

  def getUser(slug: User.Slug): IO[Option[User]]

  def getUsers(ids: Seq[User.Id]): IO[Seq[User]]


  def createAccountValidationRequest(email: Email, user: User.Id, now: Instant): IO[AccountValidationRequest]

  def getPendingAccountValidationRequest(id: UserRequest.Id, now: Instant): IO[Option[AccountValidationRequest]]

  def getPendingAccountValidationRequest(id: User.Id, now: Instant): IO[Option[AccountValidationRequest]]

  def validateAccount(id: UserRequest.Id, user: User.Id, now: Instant): IO[Done]

  def createPasswordResetRequest(email: Email, now: Instant): IO[PasswordResetRequest]

  def getPendingPasswordResetRequest(id: UserRequest.Id, now: Instant): IO[Option[PasswordResetRequest]]

  def getPendingPasswordResetRequest(email: Email, now: Instant): IO[Option[PasswordResetRequest]]

  def resetPassword(passwordReset: PasswordResetRequest, credentials: User.Credentials, now: Instant): IO[Done]


  def createGroup(data: Group.Data, by: User.Id, now: Instant): IO[Group]

  def getGroup(user: User.Id, slug: Group.Slug): IO[Option[Group]]

  def getGroups(user: User.Id, params: Page.Params): IO[Page[Group]]


  def createEvent(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event]

  def updateEvent(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done]

  def updateEventTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done]

  def getEvent(group: Group.Id, event: Event.Slug): IO[Option[Event]]

  def getEvents(group: Group.Id, params: Page.Params): IO[Page[Event]]

  def getEvents(ids: Seq[Event.Id]): IO[Seq[Event]]

  def getEventsAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]]


  def createCfp(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp]

  def getCfp(slug: Cfp.Slug): IO[Option[Cfp]]

  def getCfp(id: Cfp.Id): IO[Option[Cfp]]

  def getCfp(id: Group.Id): IO[Option[Cfp]]

  def getCfpAvailables(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]]


  def createTalk(user: User.Id, data: Talk.Data, now: Instant): IO[Talk]

  def updateTalk(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done]

  def updateTalkStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done]

  def updateTalkSlides(user: User.Id, slug: Talk.Slug)(slides: Slides, now: Instant): IO[Done]

  def updateTalkVideo(user: User.Id, slug: Talk.Slug)(video: Video, now: Instant): IO[Done]

  def getTalk(user: User.Id, slug: Talk.Slug): IO[Option[Talk]]

  def getTalks(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def getTalks(ids: Seq[Talk.Id]): IO[Seq[Talk]]


  def createProposal(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal]

  def updateProposalStatus(id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): IO[Done]

  def updateProposalSlides(id: Proposal.Id)(slides: Slides, now: Instant, user: User.Id): IO[Done]

  def updateProposalVideo(id: Proposal.Id)(video: Video, now: Instant, user: User.Id): IO[Done]

  def getProposal(id: Proposal.Id): IO[Option[Proposal]]

  def getProposal(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]]

  def getProposals(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]]

  def getProposals(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]]

  def getProposals(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]]

  def getProposals(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]
}
