package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain.{Comment, Event, Proposal, User}
import fr.gospeak.core.services.storage.CommentRepo
import fr.gospeak.infra.services.storage.sql.CommentRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Insert, Select}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._

class CommentRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CommentRepo {
  override def addComment(event: Event.Id, data: Comment.Data, by: User.Id, now: Instant): IO[Comment] =
    insert(Comment.create(event, data, by, now)).run(xa).map(_._2)

  override def getComments(event: Event.Id): IO[Seq[Comment.Full]] = selectAll(event).runList(xa)
}

object CommentRepoSql {
  private val _ = commentIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.comments
  private val tableFull = table
    .join(Tables.users, _.created_by -> _.id)
    .flatMap(_.dropField(_.event_id))
    .flatMap(_.dropField(_.proposal_id)).get


  private[sql] def insert(e: (Event.Id, Comment)): Insert[(Event.Id, Comment)] = {
    val c = e._2
    val values = fr0"${e._1}, ${Option.empty[Proposal.Id]}, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdBy}, ${c.createdAt}"
    table.insert(e, _ => values)
  }

  private[sql] def selectAll(event: Event.Id): Select[Comment.Full] =
    tableFull.select[Comment.Full](fr0"WHERE co.event_id=$event")

}
