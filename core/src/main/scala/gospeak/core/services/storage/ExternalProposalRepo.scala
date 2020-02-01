package gospeak.core.services.storage

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.core.domain.{CommonProposal, ExternalEvent, ExternalProposal, Talk, User}
import gospeak.core.domain.utils.UserCtx
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait ExternalProposalRepo extends SpeakerExternalProposalRepo with SuggestExternalProposalRepo {
  def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Done]

  def list(params: Page.Params): IO[Page[ExternalProposal]]

  def listAll(talk: Talk.Id): IO[Seq[ExternalProposal]]

  def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]]
}

trait SpeakerExternalProposalRepo {
  def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal]

  def listAllCommon(talk: Talk.Id): IO[Seq[CommonProposal]]
}

trait SuggestExternalProposalRepo {
  def listTags(): IO[Seq[Tag]]
}
