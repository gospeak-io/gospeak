package gospeak.infra.services.storage.sql

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import gospeak.core.domain.{Comment, Event, Proposal}
import gospeak.core.services.storage.CommentRepo
import gospeak.infra.services.storage.sql.CommentRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Insert, Select}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._

class CommentRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CommentRepo {
  override def addComment(event: Event.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] =
    insert(event, Comment.create(data, Comment.Kind.Event, ctx.user.id, ctx.now)).run(xa)

  override def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.Proposal, ctx.user.id, ctx.now)).run(xa)

  override def addOrgaComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: OrgaCtx): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.ProposalOrga, ctx.user.id, ctx.now)).run(xa)

  override def getComments(event: Event.Id): IO[Seq[Comment.Full]] = selectAll(event, Comment.Kind.Event).runList(xa)

  override def getComments(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[Seq[Comment.Full]] = selectAll(proposal, Comment.Kind.Proposal).runList(xa)

  override def getOrgaComments(proposal: Proposal.Id)(implicit ctx: OrgaCtx): IO[Seq[Comment.Full]] = selectAll(proposal, Comment.Kind.ProposalOrga).runList(xa)
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
