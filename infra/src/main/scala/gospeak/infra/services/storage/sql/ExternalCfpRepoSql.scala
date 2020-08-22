package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, Info, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ExternalCfpRepo
import gospeak.infra.services.storage.sql.ExternalCfpRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, Page}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}

class ExternalCfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalCfpRepo {
  override def create(event: ExternalEvent.Id, data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp] =
    insert(ExternalCfp(data, event, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Done] =
    update(cfp)(data, ctx.user.id, ctx.now).run(xa)

  override def listAllIds(): IO[Seq[ExternalCfp.Id]] = selectAllIds().runList(xa)

  override def listAll(event: ExternalEvent.Id): IO[Seq[ExternalCfp]] = selectAll(event).runList(xa)

  override def listIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonCfp]] = selectCommonPageIncoming(params).run(xa)

  override def listDuplicatesFull(p: ExternalCfp.DuplicateParams): IO[Seq[ExternalCfp.Full]] = selectDuplicatesFull(p).runList(xa)

  override def findFull(cfp: ExternalCfp.Id): IO[Option[ExternalCfp.Full]] = selectOneFull(cfp).runOption(xa)

  override def findCommon(cfp: Cfp.Slug): IO[Option[CommonCfp]] = selectOneCommon(cfp).runOption(xa)

  override def findCommon(cfp: ExternalCfp.Id): IO[Option[CommonCfp]] = selectOneCommon(cfp).runOption(xa)
}

object ExternalCfpRepoSql {
  private val _ = externalCfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalCfps
  private val tableFull = table
    .join(Tables.externalEvents.dropFields(_.name.startsWith("location_")), _.event_id -> _.id).get
  private val commonTable = Table(
    name = "((SELECT c.name, g.logo, c.begin, c.close, g.location, c.description, c.tags, null as ext_id, null  as ext_url, null    as ext_event_start, null     as ext_event_finish, null  as ext_event_url, null          as ext_tickets_url, null         as ext_videos_url, null as twitter_account, null as twitter_hashtag, c.slug as int_slug, g.id as group_id, g.slug as group_slug FROM cfps c INNER JOIN groups g ON c.group_id=g.id) " +
      "UNION (SELECT e.name, e.logo, c.begin, c.close, e.location, c.description, e.tags, c.id as ext_id, c.url as ext_url, e.start as ext_event_start, e.finish as ext_event_finish, e.url as ext_event_url, e.tickets_url as ext_tickets_url, e.videos_url as ext_videos_url,       e.twitter_account,       e.twitter_hashtag, null   as int_slug, null as group_id,   null as group_slug FROM external_cfps c INNER JOIN external_events e ON c.event_id=e.id))",
    prefix = "c",
    joins = List(),
    fields = List(
      "name", "logo", "begin", "close", "location", "description", "tags",
      "ext_id", "ext_url", "ext_event_start", "ext_event_finish", "ext_event_url", "ext_tickets_url", "ext_videos_url", "twitter_account", "twitter_hashtag",
      "int_slug", "group_id", "group_slug").map(Field(_, "c")),
    aggFields = List(),
    customFields = List(),
    sorts = Table.Sorts("close", "close date", Field("close", "c"), Field("name", "c")),
    search = List("name", "description", "tags").map(Field(_, "c")),
    filters = List())

  private[sql] def insert(e: ExternalCfp): Query.Insert[ExternalCfp] = {
    val values = fr0"${e.id}, ${e.event}, ${e.description}, ${e.begin}, ${e.close}, ${e.url}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[ExternalCfp](e, _ => values)
  }

  private[sql] def update(id: ExternalCfp.Id)(e: ExternalCfp.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"description=${e.description}, begin=${e.begin}, close=${e.close}, url=${e.url}, updated_at=$now, updated_by=$by"
    table.update(fields).where(fr0"id=$id")
  }

  private[sql] def selectAllIds(): Query.Select[ExternalCfp.Id] =
    table.select[ExternalCfp.Id].fields(Field("id", "ec"))

  private[sql] def selectAll(id: ExternalEvent.Id): Query.Select[ExternalCfp] =
    table.select[ExternalCfp].where(fr0"ec.event_id=$id")

  private[sql] def selectOneFull(id: ExternalCfp.Id): Query.Select[ExternalCfp.Full] =
    tableFull.select[ExternalCfp.Full].where(fr0"ec.id=$id").one

  private[sql] def selectOneCommon(slug: Cfp.Slug): Query.Select[CommonCfp] =
    commonTable.select[CommonCfp].where(fr0"c.slug=$slug").one

  private[sql] def selectOneCommon(id: ExternalCfp.Id): Query.Select[CommonCfp] =
    commonTable.select[CommonCfp].where(fr0"c.id=$id").one

  private[sql] def selectCommonPageIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[CommonCfp] =
    commonTable.selectPage[CommonCfp](params, adapt(ctx)).where(fr0"c.close IS NULL OR c.close >= ${ctx.now}")

  private[sql] def selectDuplicatesFull(p: ExternalCfp.DuplicateParams): Query.Select[ExternalCfp.Full] = {
    val filters = Seq(
      p.cfpUrl.map(v => fr0"ec.url LIKE ${"%" + v + "%"}"),
      p.cfpEndDate.map(v => fr0"ec.close=$v")
    ).flatten
    if (filters.isEmpty) {
      tableFull.select[ExternalCfp.Full].where(fr0"ec.id='no-match'")
    } else {
      tableFull.select[ExternalCfp.Full].where(filters.reduce(_ ++ fr0" OR " ++ _))
    }
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
