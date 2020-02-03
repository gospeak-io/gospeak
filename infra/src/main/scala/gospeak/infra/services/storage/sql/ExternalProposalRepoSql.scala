package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain._
import gospeak.core.domain.utils.{Info, UserCtx}
import gospeak.core.services.storage.ExternalProposalRepo
import gospeak.infra.services.storage.sql.ExternalProposalRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils._
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, Page, Tag}

class ExternalProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalProposalRepo {
  override def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal] =
    insert(ExternalProposal(data, talk, event, speakers, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def remove(id: ExternalProposal.Id)(implicit ctx: UserCtx): IO[Done] = delete(id, ctx.user.id).run(xa)

  override def list(params: Page.Params): IO[Page[ExternalProposal]] = selectPage(params).run(xa)

  override def listPageCommon(talk: Talk.Id, params: Page.Params): IO[Page[CommonProposal]] = selectPageCommon(talk, params).run(xa)

  override def listAllCommon(talk: Talk.Id): IO[Seq[CommonProposal]] = selectAllCommon(talk).runList(xa)

  override def listAll(talk: Talk.Id): IO[Seq[ExternalProposal]] = selectAll(talk).runList(xa)

  override def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]] = selectOne(id).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ExternalProposalRepoSql {

  import GenericQuery._

  private val _ = externalProposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalProposals
  private val commonTable = Table(
    name = "((SELECT p.id, false as external, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, c.id as cfp_id, c.slug as cfp_slug, c.name as cfp_name, e.id as event_id, e.slug as event_slug, e.name as event_name, e.start as event_start, null as event_ext_id, null   as event_ext_name, null    as event_ext_start, p.title, p.status, p.duration, p.tags, p.created_at, p.created_by, p.updated_at, p.updated_by FROM proposals p          INNER JOIN talks t ON p.talk_id=t.id LEFT OUTER JOIN events e     ON p.event_id=e.id INNER JOIN cfps c ON p.cfp_id=c.id) " +
      "UNION (SELECT p.id, true  as external, t.id as talk_id, t.slug as talk_slug, t.duration as talk_duration, null as cfp_id, null   as cfp_slug, null   as cfp_name, null as event_id, null   as event_slug, null   as event_name, null    as event_start, e.id as event_ext_id, e.name as event_ext_name, e.start as event_ext_start, p.title, p.status, p.duration, p.tags, p.created_at, p.created_by, p.updated_at, p.updated_by FROM external_proposals p INNER JOIN talks t ON p.talk_id=t.id INNER JOIN external_events e ON p.event_id=e.id))",
    prefix = "p",
    joins = Seq(),
    fields = Seq("id", "external", "talk_id", "talk_slug", "talk_duration", "cfp_id", "cfp_slug", "cfp_name", "event_id", "event_slug", "event_name", "event_start", "event_ext_id", "event_ext_name", "event_ext_start", "title", "status", "duration", "tags", "created_at", "created_by", "updated_at", "updated_by").map(Field(_, "p")),
    aggFields = Seq(),
    customFields = Seq(),
    sorts = Sorts(Seq("-created_at").map(Field(_, "p")), Map()),
    search = Seq("title").map(Field(_, "p")))

  private[sql] def insert(e: ExternalProposal): Insert[ExternalProposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.event}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.url}, ${e.tags}, " ++ insertInfo(e.info)
    table.insert[ExternalProposal](e, _ => values)
  }

  private[sql] def update(id: ExternalProposal.Id)(e: ExternalProposal.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"status=${e.status}, title=${e.title}, duration=${e.duration}, description=${e.description}, message=${e.message}, slides=${e.slides}, video=${e.video}, url=${e.url}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE id=$id AND speakers LIKE ${"%" + by.value + "%"}")
  }

  private[sql] def delete(id: ExternalProposal.Id, by: User.Id): Delete =
    table.delete(fr0"WHERE ep.id=$id AND ep.speakers LIKE ${"%" + by.value + "%"}")

  private[sql] def selectOne(id: ExternalProposal.Id): Select[ExternalProposal] =
    table.selectOne[ExternalProposal](fr0"WHERE ep.id=$id", Seq())

  private[sql] def selectPage(params: Page.Params): SelectPage[ExternalProposal] =
    table.selectPage[ExternalProposal](params)

  private[sql] def selectPageCommon(talk: Talk.Id, params: Page.Params): SelectPage[CommonProposal] =
    commonTable.selectPage[CommonProposal](params, fr0"WHERE p.talk_id=$talk")

  private[sql] def selectAllCommon(talk: Talk.Id): Select[CommonProposal] =
    commonTable.select[CommonProposal](fr0"WHERE p.talk_id=$talk")

  private[sql] def selectAll(talk: Talk.Id): Select[ExternalProposal] =
    table.select[ExternalProposal](fr0"WHERE ep.talk_id=$talk")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ep")), Seq())
}
