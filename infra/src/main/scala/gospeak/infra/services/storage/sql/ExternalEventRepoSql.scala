package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{BasicCtx, Info, UserAwareCtx, UserCtx}
import gospeak.core.domain.{CommonEvent, Event, ExternalEvent, User}
import gospeak.core.services.storage.ExternalEventRepo
import gospeak.infra.services.storage.sql.ExternalEventRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, Logo, Page, Tag}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}
import gospeak.libs.sql.dsl

class ExternalEventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalEventRepo {
  override def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent] =
    insert(ExternalEvent(data, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def listAllIds()(implicit ctx: UserAwareCtx): IO[List[ExternalEvent.Id]] = selectAllIds().runList(xa)

  override def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[ExternalEvent]] = selectPage(params).run(xa)

  override def listCommon(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonEvent]] = selectPageCommon(params).run(xa)

  override def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]] = selectOne(id).runOption(xa)

  override def listTags(): IO[List[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def listLogos(): IO[List[Logo]] = selectLogos().runList(xa).map(_.flatten.distinct)
}

object ExternalEventRepoSql {

  import GenericQuery._

  private val _ = externalEventIdMeta // for intellij not remove DoobieMappings import
  val table: Table = Tables.externalEvents
  private val tableSelect = table.dropFields(_.name.startsWith("location_")).filters(
    Table.Filter.Enum.fromEnum("type", "Type", "ee.kind", Event.Kind.all.map(k => k.value.toLowerCase -> k.value)),
    Table.Filter.Bool.fromNullable("video", "With video", "ee.videos_url"))
  val commonTable: Table = Table(
    name = "((SELECT e.name, e.kind, e.start, v.address as location, g.social_twitter as twitter_account, null as twitter_hashtag, e.tags, null as ext_id, null as ext_logo, null as ext_description, null as ext_url, null as ext_tickets, null as ext_videos, e.id as int_id, e.slug as int_slug, e.description as int_description, g.id as int_group_id, g.slug as int_group_slug, g.name as int_group_name, g.logo as int_group_logo, c.id as int_cfp_id, c.slug as int_cfp_slug, c.name as int_cfp_name, v.id as int_venue_id, pa.name as int_venue_name, pa.logo as int_venue_logo, e.created_at, e.created_by, e.updated_at, e.updated_by FROM events e INNER JOIN groups g ON e.group_id=g.id LEFT OUTER JOIN cfps c ON e.cfp_id=c.id LEFT OUTER JOIN venues v ON e.venue=v.id LEFT OUTER JOIN partners pa ON v.partner_id=pa.id WHERE e.published IS NOT NULL) " +
      "UNION (SELECT ee.name, ee.kind, ee.start, ee.location, ee.twitter_account, ee.twitter_hashtag, ee.tags, ee.id as ext_id, ee.logo as ext_logo, ee.description as ext_description, ee.url as ext_url, ee.tickets_url as ext_tickets, ee.videos_url as ext_videos, null as int_id, null as int_slug, null as int_description, null as int_group_id, null as int_group_slug, null as int_group_name, null as int_group_logo, null as int_cfp_id, null as int_cfp_slug, null as int_cfp_name, null as int_venue_id, null as int_venue_name, null as int_venue_logo, ee.created_at, ee.created_by, ee.updated_at, ee.updated_by FROM external_events ee))",
    prefix = "e",
    joins = List(),
    fields = List(
      "name", "kind", "start", "location", "twitter_account", "twitter_hashtag", "tags",
      "ext_id", "ext_logo", "ext_description", "ext_url", "ext_tickets", "ext_videos",
      "int_id", "int_slug", "int_description", "int_group_id", "int_group_slug", "int_group_name", "int_group_logo", "int_cfp_id", "int_cfp_slug", "int_cfp_name", "int_venue_id", "int_venue_name", "int_venue_logo",
      "created_at", "created_by", "updated_at", "updated_by").map(Field(_, "e")),
    aggFields = List(),
    customFields = List(),
    sorts = Table.Sorts("start", Field("-start", "e"), Field("-created_at", "e")),
    search = List("name", "kind", "location", "twitter_account", "tags", "int_group_name", "int_cfp_name", "int_description", "ext_description").map(Field(_, "e")),
    filters = List(
      Table.Filter.Enum.fromEnum("type", "Type", "e.kind", Event.Kind.all.map(k => k.value.toLowerCase -> k.value)),
      Table.Filter.Bool.fromNullable("video", "With video", "e.ext_videos"),
      Table.Filter.Bool("past", "Is past", aggregation = false, ctx => fr0"e.start < ${ctx.now}", ctx => fr0"e.start > ${ctx.now}")))
  private val EXTERNAL_EVENTS_SELECT = EXTERNAL_EVENTS.dropFields(_.name.startsWith("location_")).filters(
    dsl.Table.Filter.Enum.fromValues("type", "Type", EXTERNAL_EVENTS.KIND, Event.Kind.all.map(k => k.value.toLowerCase -> k)),
    dsl.Table.Filter.Bool.fromNullable("video", "With video", EXTERNAL_EVENTS.VIDEOS_URL))
  private val COMMON_EVENTS = {
    val (g, e, c, v, p, ee) = (GROUPS, EVENTS, CFPS, VENUES, PARTNERS, EXTERNAL_EVENTS)
    val internalEvents = e.joinOn(e.GROUP_ID).joinOn(e.CFP_ID).joinOn(e.VENUE).joinOn(v.PARTNER_ID, _.LeftOuter).select.fields(
      e.NAME, e.KIND, e.START, v.ADDRESS.as("location"), g.SOCIAL_TWITTER.as("twitter_account"), ee.TWITTER_HASHTAG.asNull, e.TAGS,
      ee.ID.asNull("ext_id"), ee.LOGO.asNull("ext_logo"), ee.DESCRIPTION.asNull("ext_description"), ee.URL.asNull("ext_url"), ee.TICKETS_URL.asNull("ext_tickets"), ee.VIDEOS_URL.asNull("ext_videos"),
      e.ID.as("int_id"), e.SLUG.as("int_slug"), e.DESCRIPTION.as("int_description"),
      g.ID.as("int_group_id"), g.SLUG.as("int_group_slug"), g.NAME.as("int_group_name"), g.LOGO.as("int_group_logo"),
      c.ID.as("int_cfp_id"), c.SLUG.as("int_cfp_slug"), c.NAME.as("int_cfp_name"),
      v.ID.as("int_venue_id"), p.NAME.as("int_venue_name"), p.LOGO.as("int_venue_logo"), e.CREATED_AT, e.CREATED_BY, e.UPDATED_AT, e.UPDATED_BY
    ).where(e.PUBLISHED.notNull).orderBy()
    val externalEvents = EXTERNAL_EVENTS.select.fields(
      ee.NAME, ee.KIND, ee.START, ee.LOCATION, ee.TWITTER_ACCOUNT, ee.TWITTER_HASHTAG, ee.TAGS,
      ee.ID.as("ext_id"), ee.LOGO.as("ext_logo"), ee.DESCRIPTION.as("ext_description"), ee.URL.as("ext_url"), ee.TICKETS_URL.as("ext_tickets"), ee.VIDEOS_URL.as("ext_videos"),
      e.ID.asNull("int_id"), e.SLUG.asNull("int_slug"), e.DESCRIPTION.asNull("int_description"),
      g.ID.asNull("int_group_id"), g.SLUG.asNull("int_group_slug"), g.NAME.asNull("int_group_name"), g.LOGO.asNull("int_group_logo"),
      c.ID.asNull("int_cfp_id"), c.SLUG.asNull("int_cfp_slug"), c.NAME.asNull("int_cfp_name"),
      v.ID.asNull("int_venue_id"), p.NAME.asNull("int_venue_name"), p.LOGO.asNull("int_venue_logo"), ee.CREATED_AT, ee.CREATED_BY, ee.UPDATED_AT, ee.UPDATED_BY
    ).orderBy()
    val ce = internalEvents.union(externalEvents, alias = Some("e"), sorts = List(("start", "start", List("-start", "-created_at"))), search = List("name", "kind", "location", "twitter_account", "tags", "int_group_name", "int_cfp_name", "int_description", "ext_description"))
    ce.filters(
      dsl.Table.Filter.Enum.fromValues("type", "Type", ce.kind, Event.Kind.all.map(k => k.value.toLowerCase -> k)),
      dsl.Table.Filter.Bool.fromNullable("video", "With video", ce.ext_videos),
      new dsl.Table.Filter.Bool("past", "Is past", aggregation = false, ctx => ce.start.lt(ctx.now), ctx => ce.start.gt(ctx.now)))
  }

  private[sql] def insert(e: ExternalEvent): Query.Insert[ExternalEvent] = {
    val values = fr0"${e.id}, ${e.name}, ${e.kind}, ${e.logo}, ${e.description}, ${e.start}, ${e.finish}, " ++ insertLocation(e.location) ++ fr0", ${e.url}, ${e.tickets}, ${e.videos}, ${e.twitterAccount}, ${e.twitterHashtag}, ${e.tags}, " ++ insertInfo(e.info)
    val q1 = table.insert[ExternalEvent](e, _ => values)
    val q2 = EXTERNAL_EVENTS.insert.values(e.id, e.name, e.kind, e.logo, e.description, e.start, e.finish, e.location, e.location.map(_.id), e.location.map(_.geo.lat), e.location.map(_.geo.lng), e.location.flatMap(_.locality), e.location.map(_.country), e.url, e.tickets, e.videos, e.twitterAccount, e.twitterHashtag, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(id: ExternalEvent.Id)(e: ExternalEvent.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"name=${e.name}, kind=${e.kind}, logo=${e.logo}, description=${e.description}, start=${e.start}, finish=${e.finish}, " ++ updateLocation(e.location) ++ fr0", url=${e.url}, tickets_url=${e.tickets}, videos_url=${e.videos}, twitter_account=${e.twitterAccount}, twitter_hashtag=${e.twitterHashtag}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(fr0"ee.id=$id")
    val q2 = EXTERNAL_EVENTS.update.set(_.NAME, e.name).set(_.KIND, e.kind).set(_.LOGO, e.logo).set(_.DESCRIPTION, e.description).set(_.START, e.start).set(_.FINISH, e.finish)
      .set(_.LOCATION, e.location).set(_.LOCATION_ID, e.location.map(_.id)).set(_.LOCATION_LAT, e.location.map(_.geo.lat)).set(_.LOCATION_LNG, e.location.map(_.geo.lng)).set(_.LOCATION_LOCALITY, e.location.flatMap(_.locality)).set(_.LOCATION_COUNTRY, e.location.map(_.country))
      .set(_.URL, e.url).set(_.TICKETS_URL, e.tickets).set(_.VIDEOS_URL, e.videos).set(_.TWITTER_ACCOUNT, e.twitterAccount).set(_.TWITTER_HASHTAG, e.twitterHashtag).set(_.TAGS, e.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID.is(id))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(id: ExternalEvent.Id): Query.Select[ExternalEvent] = {
    val q1 = tableSelect.select[ExternalEvent].where(fr0"ee.id=$id").one
    val q2 = EXTERNAL_EVENTS_SELECT.select.where(_.id.is(id)).option[ExternalEvent](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllIds()(implicit ctx: UserAwareCtx): Query.Select[ExternalEvent.Id] = {
    val q1 = table.select[ExternalEvent.Id].fields(Field("id", "ee"))
    val q2 = EXTERNAL_EVENTS.select.withFields(_.ID).all[ExternalEvent.Id]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[ExternalEvent] = {
    val q1 = tableSelect.selectPage[ExternalEvent](params, adapt(ctx))
    val q2 = EXTERNAL_EVENTS_SELECT.select.page[ExternalEvent](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[CommonEvent] = {
    val q1 = commonTable.selectPage[CommonEvent](params, adapt(ctx))
    val q2 = COMMON_EVENTS.select.page[CommonEvent](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectTags(): Query.Select[List[Tag]] = {
    val q1 = table.select[List[Tag]].fields(Field("tags", "ee"))
    val q2 = EXTERNAL_EVENTS.select.withFields(_.TAGS).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectLogos(): Query.Select[Option[Logo]] = {
    val q1 = table.select[Option[Logo]].fields(Field("logo", "ee")).where(fr0"ee.logo IS NOT NULL")
    val q2 = EXTERNAL_EVENTS.select.withFields(_.LOGO).where(_.LOGO.notNull).all[Option[Logo]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
