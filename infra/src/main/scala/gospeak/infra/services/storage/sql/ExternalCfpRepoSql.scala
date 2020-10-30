package gospeak.infra.services.storage.sql

import java.time.{Instant, LocalDateTime}

import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.{Query, TableField}
import gospeak.core.domain._
import gospeak.core.domain.utils.{Info, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.ExternalCfpRepo
import gospeak.infra.services.storage.sql.ExternalCfpRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{CFPS, EXTERNAL_CFPS, EXTERNAL_EVENTS, GROUPS}
import gospeak.infra.services.storage.sql.database.tables.EXTERNAL_CFPS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.Page

class ExternalCfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalCfpRepo {
  override def create(event: ExternalEvent.Id, data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp] = {
    val cfp = ExternalCfp(data, event, Info(ctx.user.id, ctx.now))
    insert(cfp).run(xa).map(_ => cfp)
  }

  override def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Unit] =
    update(cfp)(data, ctx.user.id, ctx.now).run(xa)

  override def listAllIds(): IO[List[ExternalCfp.Id]] = selectAllIds().run(xa)

  override def listAll(event: ExternalEvent.Id): IO[List[ExternalCfp]] = selectAll(event).run(xa)

  override def listIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonCfp]] = selectCommonPageIncoming(params).run(xa).map(_.fromSql)

  override def listDuplicatesFull(p: ExternalCfp.DuplicateParams): IO[List[ExternalCfp.Full]] = selectDuplicatesFull(p).run(xa)

  override def findFull(cfp: ExternalCfp.Id): IO[Option[ExternalCfp.Full]] = selectOneFull(cfp).run(xa)

  override def findCommon(cfp: Cfp.Slug): IO[Option[CommonCfp]] = selectOneCommon(cfp).run(xa)

  override def findCommon(cfp: ExternalCfp.Id): IO[Option[CommonCfp]] = selectOneCommon(cfp).run(xa)
}

object ExternalCfpRepoSql {
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

  private[sql] def insert(e: ExternalCfp): Query.Insert[EXTERNAL_CFPS] =
  // EXTERNAL_CFPS.insert.values(e.id, e.event, e.description, e.begin, e.close, e.url, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    EXTERNAL_CFPS.insert.values(fr0"${e.id}, ${e.event}, ${e.description}, ${e.begin}, ${e.close}, ${e.url}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(id: ExternalCfp.Id)(e: ExternalCfp.Data, by: User.Id, now: Instant): Query.Update[EXTERNAL_CFPS] =
    EXTERNAL_CFPS.update.set(_.DESCRIPTION, e.description).set(_.BEGIN, e.begin).set(_.CLOSE, e.close).set(_.URL, e.url).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID is id)

  private[sql] def selectAllIds(): Query.Select.All[ExternalCfp.Id] =
    EXTERNAL_CFPS.select.fields(EXTERNAL_CFPS.ID).all[ExternalCfp.Id]

  private[sql] def selectAll(id: ExternalEvent.Id): Query.Select.All[ExternalCfp] =
    EXTERNAL_CFPS.select.where(_.EVENT_ID is id).all[ExternalCfp]

  private[sql] def selectOneFull(id: ExternalCfp.Id): Query.Select.Optional[ExternalCfp.Full] =
    EXTERNAL_CFPS_FULL.select.where(EXTERNAL_CFPS.ID is id).option[ExternalCfp.Full](limit = true)

  private[sql] def selectOneCommon(slug: Cfp.Slug): Query.Select.Optional[CommonCfp] =
    COMMON_CFPS.select.where(_.int_slug[Cfp.Slug] is slug).option[CommonCfp](limit = true)

  private[sql] def selectOneCommon(id: ExternalCfp.Id): Query.Select.Optional[CommonCfp] =
    COMMON_CFPS.select.where(_.ext_id[ExternalCfp.Id] is id).option[CommonCfp](limit = true)

  private[sql] def selectCommonPageIncoming(params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[CommonCfp] =
    COMMON_CFPS.select.where(c => c.close.isNull or c.close[LocalDateTime].gte(TimeUtils.toLocalDateTime(ctx.now))).page[CommonCfp](params.toSql, ctx.toSql)

  private[sql] def selectDuplicatesFull(p: ExternalCfp.DuplicateParams): Query.Select.All[ExternalCfp.Full] = {
    val filters = List(
      p.cfpUrl.map(u => EXTERNAL_CFPS.URL.like("%" + u + "%")),
      p.cfpEndDate.map(EXTERNAL_CFPS.CLOSE is _)
    ).flatten
    if (filters.isEmpty) {
      EXTERNAL_CFPS_FULL.select.where(TableField[String]("id", Some("ec")) is "no-match").all[ExternalCfp.Full]
    } else {
      EXTERNAL_CFPS_FULL.select.where(filters.reduce(_ or _)).all[ExternalCfp.Full]
    }
  }
}
