package gospeak.infra.services.storage.sql

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{OrgaCtx, UserCtx}
import gospeak.core.domain.{Comment, Event, Proposal}
import gospeak.core.services.storage.CommentRepo
import gospeak.infra.services.storage.sql.CommentRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.COMMENTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.sql.doobie.Query

class CommentRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CommentRepo {
  override def addComment(event: Event.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] =
    insert(event, Comment.create(data, Comment.Kind.Event, ctx.user.id, ctx.now)).run(xa)

  override def addComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: UserCtx): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.Proposal, ctx.user.id, ctx.now)).run(xa)

  override def addOrgaComment(proposal: Proposal.Id, data: Comment.Data)(implicit ctx: OrgaCtx): IO[Comment] =
    insert(proposal, Comment.create(data, Comment.Kind.ProposalOrga, ctx.user.id, ctx.now)).run(xa)

  override def getComments(event: Event.Id): IO[List[Comment.Full]] = selectAll(event, Comment.Kind.Event).runList(xa)

  override def getComments(proposal: Proposal.Id)(implicit ctx: UserCtx): IO[List[Comment.Full]] = selectAll(proposal, Comment.Kind.Proposal).runList(xa)

  override def getOrgaComments(proposal: Proposal.Id)(implicit ctx: OrgaCtx): IO[List[Comment.Full]] = selectAll(proposal, Comment.Kind.ProposalOrga).runList(xa)
}

object CommentRepoSql {
  private val _ = commentIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.comments
  private val tableFull = table
    .join(Tables.users, _.created_by -> _.id)
    .flatMap(_.dropField(_.event_id))
    .flatMap(_.dropField(_.proposal_id)).get
  private val TABLE_FULL = COMMENTS.joinOn(_.CREATED_BY).dropFields(COMMENTS.EVENT_ID, COMMENTS.PROPOSAL_ID)

  private[sql] def insert(e: Event.Id, c: Comment): Query.Insert[Comment] = {
    val values = fr0"$e, ${Option.empty[Proposal.Id]}, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdAt}, ${c.createdBy}"
    val q1 = table.insert[Comment](c, _ => values)
    val q2 = COMMENTS.insert.values(e, Option.empty[Proposal.Id], c.id, c.kind, c.answers, c.text, c.createdAt, c.createdBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def insert(e: Proposal.Id, c: Comment): Query.Insert[Comment] = {
    val values = fr0"${Option.empty[Event.Id]}, $e, ${c.id}, ${c.kind}, ${c.answers}, ${c.text}, ${c.createdAt}, ${c.createdBy}"
    val q1 = table.insert[Comment](c, _ => values)
    val q2 = COMMENTS.insert.values(Option.empty[Event.Id], e, c.id, c.kind, c.answers, c.text, c.createdAt, c.createdBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(event: Event.Id, kind: Comment.Kind): Query.Select[Comment.Full] = {
    val q1 = tableFull.select[Comment.Full].where(fr0"co.event_id=$event AND co.kind=$kind")
    val q2 = TABLE_FULL.select.where(COMMENTS.EVENT_ID.is(event) and COMMENTS.KIND.is(kind)).all[Comment.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(proposal: Proposal.Id, kind: Comment.Kind): Query.Select[Comment.Full] = {
    val q1 = tableFull.select[Comment.Full].where(fr0"co.proposal_id=$proposal AND co.kind=$kind")
    val q2 = TABLE_FULL.select.where(COMMENTS.PROPOSAL_ID.is(proposal) and COMMENTS.KIND.is(kind)).all[Comment.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }
}
