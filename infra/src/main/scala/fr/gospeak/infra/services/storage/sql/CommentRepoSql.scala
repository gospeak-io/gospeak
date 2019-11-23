package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import fr.gospeak.core.domain.{Comment, Event, Proposal, User}
import fr.gospeak.core.services.storage.CommentRepo
import fr.gospeak.infra.services.storage.sql.CommentRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Insert, Select}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._

class CommentRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CommentRepo {
  override def addComment(event: Event.Id, data: Comment.Data, by: User.Id, now: Instant): IO[Comment] =
    insert(event, Comment.create(data, Comment.Kind.Event, by, now)).run(xa)

  override def addComment(proposal: Proposal.Id, data: Comment.Data, by: User.Id, now: Instant): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.Proposal, by, now)).run(xa)

  override def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.Proposal, ctx.user.id, ctx.now)).run(xa)

  override def addOrgaComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: OrgaCtx): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.ProposalOrga, ctx.user.id, ctx.now)).run(xa)

  override def getComments(event: Event.Id): IO[Seq[Comment.Full]] = selectAll(event, Comment.Kind.Event).runList(xa)

  override def getComments(proposal: Proposal.Id): IO[Seq[Comment.Full]] = selectAll(proposal, Comment.Kind.Proposal).runList(xa)

  override def getOrgaComments(proposal: Proposal.Id): IO[Seq[Comment.Full]] = selectAll(proposal, Comment.Kind.ProposalOrga).runList(xa)
}

object CommentRepoSql {
  private val _ = commentIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.comments
  private val tableFull = table
    .join(Tables.users, _.created_by -> _.id)
    .flatMap(_.dropField(_.event_id))
    .flatMap(_.dropField(_.proposal_id)).get

  private[sql] def insert(e: Event.Id, c: Comment): Insert[Comment] = {
    val values = fr0"$e, ${Option.empty[Proposal.Id]}, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdAt}, ${c.createdBy}"
    table.insert(c, _ => values)
  }

  private[sql] def insert(e: Proposal.Id, c: Comment): Insert[Comment] = {
    val values = fr0"${Option.empty[Event.Id]}, $e, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdAt}, ${c.createdBy}"
    table.insert(c, _ => values)
  }

  private[sql] def selectAll(event: Event.Id, kind: Comment.Kind): Select[Comment.Full] =
    tableFull.select[Comment.Full](fr0"WHERE co.event_id=$event AND co.kind=$kind")

  private[sql] def selectAll(proposal: Proposal.Id, kind: Comment.Kind): Select[Comment.Full] =
    tableFull.select[Comment.Full](fr0"WHERE co.proposal_id=$proposal AND co.kind=$kind")
}
