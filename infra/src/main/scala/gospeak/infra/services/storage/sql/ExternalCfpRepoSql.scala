package gospeak.infra.services.storage.sql

import java.time.{Instant, LocalDateTime}

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, Info, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ExternalCfpRepo
import gospeak.infra.services.storage.sql.ExternalCfpRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{CFPS, EXTERNAL_CFPS, EXTERNAL_EVENTS, GROUPS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.{Done, Page}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}
import gospeak.libs.sql.dsl.TableField

class ExternalCfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalCfpRepo {
  override def create(event: ExternalEvent.Id, data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp] =
    insert(ExternalCfp(data, event, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Done] =
    update(cfp)(data, ctx.user.id, ctx.now).run(xa)

  override def listAllIds(): IO[List[ExternalCfp.Id]] = selectAllIds().runList(xa)

  override def listAll(event: ExternalEvent.Id): IO[List[ExternalCfp]] = selectAll(event).runList(xa)

  override def listIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonCfp]] = selectCommonPageIncoming(params).run(xa)

  override def listDuplicatesFull(p: ExternalCfp.DuplicateParams): IO[List[ExternalCfp.Full]] = selectDuplicatesFull(p).runList(xa)

  override def findFull(cfp: ExternalCfp.Id): IO[Option[ExternalCfp.Full]] = selectOneFull(cfp).runOption(xa)

  override def findCommon(cfp: Cfp.Slug): IO[Option[CommonCfp]] = selectOneCommon(cfp).runOption(xa)

  override def findCommon(cfp: ExternalCfp.Id): IO[Option[CommonCfp]] = selectOneCommon(cfp).runOption(xa)
}

object ExternalCfpRepoSql {
  private val _ = externalCfpIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.externalCfps
  private val tableFull = table
    .join(Tables.externalEvents.dropFields(_.name.startsWith("location_")), _.event_id -> _.id).get
  private val commonTable = Table(
    name = "((SELECT c.name, g.logo, c.begin, c.close, g.location, c.description, c.tags, null as ext_id, null as ext_url, null as ext_event_start, null as ext_event_finish, null as ext_event_url, null as ext_tickets_url, null as ext_videos_url, null as twitter_account, null as twitter_hashtag, c.slug as int_slug, g.id as group_id, g.slug as group_slug FROM cfps c INNER JOIN groups g ON c.group_id=g.id) " +
      "UNION (SELECT ee.name, ee.logo, ec.begin, ec.close, ee.location, ec.description, ee.tags, ec.id as ext_id, ec.url as ext_url, ee.start as ext_event_start, ee.finish as ext_event_finish, ee.url as ext_event_url, ee.tickets_url as ext_tickets_url, ee.videos_url as ext_videos_url, ee.twitter_account, ee.twitter_hashtag, null as int_slug, null as group_id, null as group_slug FROM external_cfps ec INNER JOIN external_events ee ON ec.event_id=ee.id))",
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
  private val EXTERNAL_CFPS_FULL = EXTERNAL_CFPS.joinOn(_.EVENT_ID).dropFields(_.name.startsWith("location_"))
  private val COMMON_CFPS = {
    val (g, c, ee, ec) = (GROUPS, CFPS, EXTERNAL_EVENTS, EXTERNAL_CFPS)
    val internalCfps = c.joinOn(_.GROUP_ID).select.fields(
      c.NAME, g.LOGO, c.BEGIN, c.CLOSE, g.LOCATION, c.DESCRIPTION, c.TAGS,
      ec.ID.asNull("ext_id"), ec.URL.asNull("ext_url"), ee.START.asNull("ext_event_start"), ee.FINISH.asNull("ext_event_finish"),
      ee.URL.asNull("ext_event_url"), ee.TICKETS_URL.asNull("ext_tickets_url"), ee.VIDEOS_URL.asNull("ext_videos_url"), ee.TWITTER_ACCOUNT.asNull, ee.TWITTER_HASHTAG.asNull,
      c.SLUG.as("int_slug"), g.ID.as("group_id"), g.SLUG.as("group_slug")).orderBy()
    val externalCfps = ec.joinOn(_.EVENT_ID).select.fields(
      ee.NAME, ee.LOGO, ec.BEGIN, ec.CLOSE, ee.LOCATION, ec.DESCRIPTION, ee.TAGS,
      ec.ID.as("ext_id"), ec.URL.as("ext_url"), ee.START.as("ext_event_start"), ee.FINISH.as("ext_event_finish"),
      ee.URL.as("ext_event_url"), ee.TICKETS_URL.as("ext_tickets_url"), ee.VIDEOS_URL.as("ext_videos_url"), ee.TWITTER_ACCOUNT, ee.TWITTER_HASHTAG,
      c.SLUG.asNull("int_slug"), g.ID.asNull("group_id"), g.SLUG.asNull("group_slug")).orderBy()
    internalCfps.union(externalCfps, alias = Some("c"), sorts = List(("close", "close date", List("close", "name"))), search = List("name", "description", "tags"))
  }

  private[sql] def insert(e: ExternalCfp): Query.Insert[ExternalCfp] = {
    val values = fr0"${e.id}, ${e.event}, ${e.description}, ${e.begin}, ${e.close}, ${e.url}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[ExternalCfp](e, _ => values)
    val q2 = EXTERNAL_CFPS.insert.values(e.id, e.event, e.description, e.begin, e.close, e.url, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(id: ExternalCfp.Id)(e: ExternalCfp.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"description=${e.description}, begin=${e.begin}, close=${e.close}, url=${e.url}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(fr0"ec.id=$id")
    val q2 = EXTERNAL_CFPS.update.set(_.DESCRIPTION, e.description).set(_.BEGIN, e.begin).set(_.CLOSE, e.close).set(_.URL, e.url).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID is id)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllIds(): Query.Select[ExternalCfp.Id] = {
    val q1 = table.select[ExternalCfp.Id].fields(Field("id", "ec"))
    val q2 = EXTERNAL_CFPS.select.fields(EXTERNAL_CFPS.ID).all[ExternalCfp.Id]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(id: ExternalEvent.Id): Query.Select[ExternalCfp] = {
    val q1 = table.select[ExternalCfp].where(fr0"ec.event_id=$id")
    val q2 = EXTERNAL_CFPS.select.where(_.EVENT_ID is id).all[ExternalCfp]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneFull(id: ExternalCfp.Id): Query.Select[ExternalCfp.Full] = {
    val q1 = tableFull.select[ExternalCfp.Full].where(fr0"ec.id=$id").one
    val q2 = EXTERNAL_CFPS_FULL.select.where(EXTERNAL_CFPS.ID is id).option[ExternalCfp.Full](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneCommon(slug: Cfp.Slug): Query.Select[CommonCfp] = {
    val q1 = commonTable.select[CommonCfp].where(fr0"c.int_slug=$slug").one
    val q2 = COMMON_CFPS.select.where(_.int_slug[Cfp.Slug] is slug).option[CommonCfp](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneCommon(id: ExternalCfp.Id): Query.Select[CommonCfp] = {
    val q1 = commonTable.select[CommonCfp].where(fr0"c.ext_id=$id").one
    val q2 = COMMON_CFPS.select.where(_.ext_id[ExternalCfp.Id] is id).option[CommonCfp](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectCommonPageIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[CommonCfp] = {
    val q1 = commonTable.selectPage[CommonCfp](params, adapt(ctx)).where(fr0"c.close IS NULL OR c.close >= ${ctx.now}")
    val q2 = COMMON_CFPS.select.where(c => c.close.isNull or c.close[Option[LocalDateTime]].gte(Some(TimeUtils.toLocalDateTime(ctx.now)))).page[CommonCfp](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectDuplicatesFull(p: ExternalCfp.DuplicateParams): Query.Select[ExternalCfp.Full] = {
    val filters = List(
      p.cfpUrl.map(v => fr0"ec.url LIKE ${"%" + v + "%"}"),
      p.cfpEndDate.map(v => fr0"ec.close=$v")
    ).flatten
    val q1 = if (filters.isEmpty) {
      tableFull.select[ExternalCfp.Full].where(fr0"ec.id=${"no-match": String}")
    } else {
      tableFull.select[ExternalCfp.Full].where(filters.reduce(_ ++ fr0" OR " ++ _))
    }
    val f = List(
      p.cfpUrl.map(u => EXTERNAL_CFPS.URL.like("%" + u + "%")),
      p.cfpEndDate.map(EXTERNAL_CFPS.CLOSE is _)
    ).flatten
    val q2 = if (f.isEmpty) {
      EXTERNAL_CFPS_FULL.select.where(TableField[String]("id", Some("ec")) is "no-match").all[ExternalCfp.Full]
    } else {
      EXTERNAL_CFPS_FULL.select.where(f.reduce(_ or _)).all[ExternalCfp.Full]
    }
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
