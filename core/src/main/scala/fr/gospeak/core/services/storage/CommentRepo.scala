package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Comment, Event, User}

trait CommentRepo extends PublicCommentRepo

trait PublicCommentRepo {
  def addComment(event: Event.Id, data: Comment.Data, by: User.Id, now: Instant): IO[Comment]

  def getComments(event: Event.Id): IO[Seq[Comment.Full]]
}
