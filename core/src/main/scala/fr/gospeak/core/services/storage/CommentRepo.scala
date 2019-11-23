package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import fr.gospeak.core.domain.{Comment, Event, Proposal, User}

trait CommentRepo extends OrgaCommentRepo with SpeakerCommentRepo with PublicCommentRepo

trait OrgaCommentRepo {
  def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment]

  def addOrgaComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: OrgaCtx): IO[Comment]

  def getComments(proposal: Proposal.Id): IO[Seq[Comment.Full]]

  def getOrgaComments(proposal: Proposal.Id): IO[Seq[Comment.Full]]
}

trait SpeakerCommentRepo {
  def addComment(proposal: Proposal.Id, data: Comment.Data, by: User.Id, now: Instant): IO[Comment]

  def getComments(proposal: Proposal.Id): IO[Seq[Comment.Full]]
}

trait PublicCommentRepo {
  def addComment(event: Event.Id, data: Comment.Data, by: User.Id, now: Instant): IO[Comment]

  def getComments(event: Event.Id): IO[Seq[Comment.Full]]
}
