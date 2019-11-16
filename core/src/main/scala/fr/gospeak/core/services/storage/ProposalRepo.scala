package fr.gospeak.core.services.storage

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain._

trait ProposalRepo extends OrgaProposalRepo with SpeakerProposalRepo with UserProposalRepo with AuthProposalRepo with SuggestProposalRepo with PublicProposalRepo

trait OrgaProposalRepo {
  val fields: ProposalFields.type = ProposalFields

  def edit(orga: User.Id, group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id)(data: Proposal.Data, now: Instant): IO[Done]

  def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done]

  def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id, by: User.Id, now: Instant): IO[Done]

  def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done]

  def cancelReject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done]

  def editSlides(cfp: Cfp.Slug, id: Proposal.Id)(slides: Slides, by: User.Id, now: Instant): IO[Done]

  def editVideo(cfp: Cfp.Slug, id: Proposal.Id)(video: Video, by: User.Id, now: Instant): IO[Done]

  def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done]

  def listFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listFull(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listFull(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]]

  def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]

  def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]]

  def firstVote(id: Proposal.Id, userId: User.Id, rating: Proposal.Vote.Rating, now: Instant): IO[Done]

  def updateVote(id: Proposal.Id, userId: User.Id, rating: Proposal.Vote.Rating, now: Instant): IO[Done]

  def countVotes(id: Proposal.Id): IO[Seq[Proposal.Vote.Rating]]
}

trait SpeakerProposalRepo {
  def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal]

  def edit(talk: Talk.Slug, cfp: Cfp.Slug)(data: Proposal.Data, by: User.Id, now: Instant): IO[Done]

  def editSlides(talk: Talk.Slug, cfp: Cfp.Slug)(slides: Slides, by: User.Id, now: Instant): IO[Done]

  def editVideo(talk: Talk.Slug, cfp: Cfp.Slug)(video: Video, by: User.Id, now: Instant): IO[Done]

  def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug)(speaker: User.Id, by: User.Id, now: Instant): IO[Done]

  def find(proposal: Proposal.Id): IO[Option[Proposal]]

  def findFull(proposal: Proposal.Id): IO[Option[Proposal.Full]]

  def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(by: User.Id): IO[Option[Proposal.Full]]

  def listFull(user: User.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listFull(talk: Talk.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def find(speaker: User.Id, talk: Talk.Slug, cfp: Cfp.Slug): IO[Option[Proposal]]
}

trait UserProposalRepo {
  def addSpeaker(proposal: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done]

  def listFull(user: User.Id, params: Page.Params): IO[Page[Proposal.Full]]
}

trait AuthProposalRepo

trait PublicProposalRepo {
  def listPublicFull(speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listPublicFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listPublicFull(ids: Seq[Proposal.Id]): IO[Seq[Proposal.Full]]

  def findPublicFull(group: Group.Id, proposal: Proposal.Id): IO[Option[Proposal.Full]]
}

trait SuggestProposalRepo {
  def listTags(): IO[Seq[Tag]]

  def listFull(group: Group.Id, params: Page.Params): IO[Page[Proposal.Full]]
}

object ProposalFields {
  val title = "title"
  val created = "created"
}
