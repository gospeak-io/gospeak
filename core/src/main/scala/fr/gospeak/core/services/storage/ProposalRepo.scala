package fr.gospeak.core.services.storage

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.libs.scalautils.domain._

trait ProposalRepo extends OrgaProposalRepo with SpeakerProposalRepo with UserProposalRepo with AuthProposalRepo with SuggestProposalRepo with PublicProposalRepo

trait OrgaProposalRepo {
  val fields: ProposalFields.type = ProposalFields

  def edit(cfp: Cfp.Slug, proposal: Proposal.Id, data: Proposal.Data)(implicit ctx: OrgaCtx): IO[Done]

  def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done]

  def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done]

  def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done]

  def reject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done]

  def cancelReject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done]

  def rate(cfp: Cfp.Slug, id: Proposal.Id, grade: Proposal.Rating.Grade)(implicit ctx: OrgaCtx): IO[Done]

  def editSlides(cfp: Cfp.Slug, id: Proposal.Id, slides: Slides)(implicit ctx: OrgaCtx): IO[Done]

  def editVideo(cfp: Cfp.Slug, id: Proposal.Id, video: Video)(implicit ctx: OrgaCtx): IO[Done]

  def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id, speaker: User.Id)(implicit ctx: OrgaCtx): IO[Done]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]]

  def listFull(group: Group.Id, speaker: User.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listFull(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal.Full]]

  def listFull(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal.Full]]

  def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]]

  def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]

  def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]]

  def findFull(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal.Full]]

  def listRatings(id: Proposal.Id): IO[Seq[Proposal.Rating.Full]]

  def listRatings(cfp: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]]

  def listRatings(user: User.Id, proposals: Seq[Proposal.Id]): IO[Seq[Proposal.Rating]]

  def listRatings(proposals: Seq[Proposal.Id])(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]]
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
