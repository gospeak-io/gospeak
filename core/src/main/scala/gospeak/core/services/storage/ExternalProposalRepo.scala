package gospeak.core.services.storage

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.core.domain.utils.{UserAwareCtx, UserCtx}
import gospeak.core.domain._
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait ExternalProposalRepo extends SpeakerExternalProposalRepo with PublicExternalProposalRepo with SuggestExternalProposalRepo

trait SpeakerExternalProposalRepo {
  def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal]

  def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Done]

  def remove(id: ExternalProposal.Id)(implicit ctx: UserCtx): IO[Done]

  def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]]

  def listCommon(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]]

  def listCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]]

  def listCurrentCommon(params: Page.Params)(implicit ctx: UserCtx): IO[Page[CommonProposal]]

  def listAllCommon(talk: Talk.Id): IO[Seq[CommonProposal]]
}

trait PublicExternalProposalRepo {
  def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]]

  def list(event: ExternalEvent.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[ExternalProposal]]

  def listAllCommon(user: User.Id, status: Proposal.Status): IO[Seq[CommonProposal]]

  def listAllCommon(talk: Talk.Id, status: Proposal.Status): IO[List[CommonProposal]]
}

trait SuggestExternalProposalRepo {
  def listTags(): IO[Seq[Tag]]
}
