package gospeak.core.services.storage

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.libs.scala.domain._

trait ProposalRepo extends OrgaProposalRepo with SpeakerProposalRepo with UserProposalRepo with AuthProposalRepo with SuggestProposalRepo with PublicProposalRepo

trait OrgaProposalRepo {
  val fields: ProposalFields.type = ProposalFields

  def edit(cfp: Cfp.Slug, proposal: Proposal.Id, data: Proposal.DataOrga)(implicit ctx: OrgaCtx): IO[Done]

  def accept(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done]

  def cancel(cfp: Cfp.Slug, id: Proposal.Id, event: Event.Id)(implicit ctx: OrgaCtx): IO[Done]

  def reject(cfp: Cfp.Slug, id: Proposal.Id, by: User.Id, now: Instant): IO[Done]

  def reject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done]

  def cancelReject(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Done]

  def rate(cfp: Cfp.Slug, id: Proposal.Id, grade: Proposal.Rating.Grade)(implicit ctx: OrgaCtx): IO[Done]

  def editSlides(cfp: Cfp.Slug, id: Proposal.Id, slides: SlidesUrl)(implicit ctx: OrgaCtx): IO[Done]

  def editVideo(cfp: Cfp.Slug, id: Proposal.Id, video: VideoUrl)(implicit ctx: OrgaCtx): IO[Done]

  def editOrgaTags(cfp: Cfp.Slug, id: Proposal.Id, orgaTags: Seq[Tag])(implicit ctx: OrgaCtx): IO[Done]

  def removeSpeaker(cfp: Cfp.Slug, id: Proposal.Id, speaker: User.Id)(implicit ctx: OrgaCtx): IO[Done]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]]

  def listFull(speaker: User.Id, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]]

  def listFull(cfp: Cfp.Slug, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]]

  def listFull(cfp: Cfp.Slug, status: Proposal.Status, params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]]

  def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]

  def listFull(ids: Seq[Proposal.Id])(implicit ctx: UserAwareCtx): IO[Seq[Proposal.Full]]

  def find(cfp: Cfp.Slug, id: Proposal.Id): IO[Option[Proposal]]

  def findFull(cfp: Cfp.Slug, id: Proposal.Id)(implicit ctx: OrgaCtx): IO[Option[Proposal.Full]]

  def listRatings(id: Proposal.Id): IO[Seq[Proposal.Rating.Full]]

  def listRatings(cfp: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]]

  def listRatings(proposals: Seq[Proposal.Id])(implicit ctx: OrgaCtx): IO[Seq[Proposal.Rating]]
}

trait SpeakerProposalRepo {
  def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[Proposal]

  def edit(talk: Talk.Slug, cfp: Cfp.Slug, data: Proposal.Data)(implicit ctx: UserCtx): IO[Done]

  def editSlides(talk: Talk.Slug, cfp: Cfp.Slug, slides: SlidesUrl)(implicit ctx: UserCtx): IO[Done]

  def editVideo(talk: Talk.Slug, cfp: Cfp.Slug, video: VideoUrl)(implicit ctx: UserCtx): IO[Done]

  def removeSpeaker(talk: Talk.Slug, cfp: Cfp.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Done]

  def find(proposal: Proposal.Id): IO[Option[Proposal]]

  def findFull(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[Option[Proposal.Full]]

  def findFull(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal.Full]]

  def listFull(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]]

  def listFull(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]]

  def find(talk: Talk.Slug, cfp: Cfp.Slug)(implicit ctx: UserCtx): IO[Option[Proposal]]
}

trait UserProposalRepo {
  def addSpeaker(proposal: Proposal.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done]

  def listFull(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Proposal.Full]]
}

trait AuthProposalRepo

trait PublicProposalRepo {
  def listAllPublicIds()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Proposal.Id)]]

  def listAllPublicFull(speaker: User.Id)(implicit ctx: UserAwareCtx): IO[List[Proposal.Full]]

  def listPublicFull(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Proposal.Full]]

  def listPublic(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]

  def listPublicFull(ids: Seq[Proposal.Id])(implicit ctx: UserAwareCtx): IO[Seq[Proposal.Full]]

  def findPublicFull(group: Group.Id, proposal: Proposal.Id)(implicit ctx: UserAwareCtx): IO[Option[Proposal.Full]]
}

trait SuggestProposalRepo {
  def listTags(): IO[Seq[Tag]]

  def listOrgaTags()(implicit ctx: OrgaCtx): IO[Seq[Tag]]

  def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Proposal.Full]]
}

object ProposalFields {
  val title = "title"
  val created = "created"
}
