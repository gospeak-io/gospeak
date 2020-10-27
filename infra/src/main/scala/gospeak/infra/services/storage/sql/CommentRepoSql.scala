package gospeak.infra.services.storage.sql

import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import gospeak.core.domain.{Comment, Event, Proposal}
import gospeak.core.services.storage.CommentRepo
import gospeak.infra.services.storage.sql.CommentRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.COMMENTS
import gospeak.infra.services.storage.sql.database.tables.COMMENTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.sql.dsl.Query

class CommentRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CommentRepo {
  override def addComment(event: Event.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] = {
    val comment = Comment.create(data, Comment.Kind.Event, ctx.user.id, ctx.now)
    insert(event, comment).run(xa).map(_ => comment)
  }

  override def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] = {
    val comment = Comment.create(data, Comment.Kind.Proposal, ctx.user.id, ctx.now)
    insert(proposal, comment).run(xa).map(_ => comment)
  }

  override def addOrgaComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: OrgaCtx): IO[Comment] = {
    val comment = Comment.create(data, Comment.Kind.ProposalOrga, ctx.user.id, ctx.now)
    insert(proposal, comment).run(xa).map(_ => comment)
  }

  override def getComments(event: Event.Id): IO[List[Comment.Full]] = selectAll(event, Comment.Kind.Event).run(xa)

  override def getComments(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[List[Comment.Full]] = selectAll(proposal, Comment.Kind.Proposal).run(xa)

  override def getOrgaComments(proposal: Proposal.Id)(implicit ctx: OrgaCtx): IO[List[Comment.Full]] = selectAll(proposal, Comment.Kind.ProposalOrga).run(xa)
}

object CommentRepoSql {
  private val TABLE_FULL = COMMENTS.joinOn(_.CREATED_BY).dropFields(COMMENTS.EVENT_ID, COMMENTS.PROPOSAL_ID)

  private[sql] def insert(e: Event.Id, c: Comment): Query.Insert[COMMENTS] =
  // COMMENTS.insert.values(e, Option.empty[Proposal.Id], c.id, c.kind, c.answers, c.text, c.createdAt, c.createdBy)
    COMMENTS.insert.values(fr0"$e, ${Option.empty[Proposal.Id]}, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdAt}, ${c.createdBy}")

  private[sql] def insert(e: Proposal.Id, c: Comment): Query.Insert[COMMENTS] =
  // COMMENTS.insert.values(Option.empty[Event.Id], e, c.id, c.kind, c.answers, c.text, c.createdAt, c.createdBy)
    COMMENTS.insert.values(fr0"${Option.empty[Event.Id]}, $e, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdAt}, ${c.createdBy}")

  private[sql] def selectAll(event: Event.Id, kind: Comment.Kind): Query.Select.All[Comment.Full] =
    TABLE_FULL.select.where(COMMENTS.EVENT_ID.is(event) and COMMENTS.KIND.is(kind)).all[Comment.Full]

  private[sql] def selectAll(proposal: Proposal.Id, kind: Comment.Kind): Query.Select.All[Comment.Full] =
    TABLE_FULL.select.where(COMMENTS.PROPOSAL_ID.is(proposal) and COMMENTS.KIND.is(kind)).all[Comment.Full]
}
