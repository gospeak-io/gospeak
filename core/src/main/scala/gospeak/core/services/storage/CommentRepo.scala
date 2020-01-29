package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import gospeak.core.domain.{Comment, Event, Proposal}

trait CommentRepo extends OrgaCommentRepo with SpeakerCommentRepo with PublicCommentRepo

trait OrgaCommentRepo {
  def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment]

  def addOrgaComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: OrgaCtx): IO[Comment]

  def getComments(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[Seq[Comment.Full]]

  def getOrgaComments(proposal: Proposal.Id)(implicit ctx: OrgaCtx): IO[Seq[Comment.Full]]
}

trait SpeakerCommentRepo {
  def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment]

  def getComments(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[Seq[Comment.Full]]
}

trait PublicCommentRepo {
  def addComment(event: Event.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment]

  def getComments(event: Event.Id): IO[Seq[Comment.Full]]
}
