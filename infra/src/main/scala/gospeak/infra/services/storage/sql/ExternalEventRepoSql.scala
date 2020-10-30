package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.{Query, Table}
import gospeak.core.domain.utils.{Info, UserAwareCtx, UserCtx}
import gospeak.core.domain.{CommonEvent, Event, ExternalEvent, User}
import gospeak.core.services.storage.ExternalEventRepo
import gospeak.infra.services.storage.sql.ExternalEventRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.EXTERNAL_EVENTS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericQuery._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.StringUtils
import gospeak.libs.scala.domain.{Logo, Page, Tag}

class ExternalEventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalEventRepo {
  override def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent] = {
    val event = ExternalEvent(data, Info(ctx.user.id, ctx.now))
    insert(event).run(xa).map(_ => event)
  }

  override def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Unit] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def listAllIds()(implicit ctx: UserAwareCtx): IO[List[ExternalEvent.Id]] = selectAllIds().run(xa)

  override def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[ExternalEvent]] = selectPage(params).run(xa).map(_.fromSql)

  override def listCommon(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonEvent]] = selectPageCommon(params).run(xa).map(_.fromSql)

  override def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]] = selectOne(id).run(xa)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)

  override def listLogos(): IO[List[Logo]] = selectLogos().run(xa).map(_.flatten.distinct)
}

object ExternalEventRepoSql {
  val FILTERS: List[Table.Filter] = List(
    Table.Filter.Enum.fromValues("type", "Type", EXTERNAL_EVENTS.KIND, Event.Kind.all.map(k => (StringUtils.slugify(k.value), k.label, k))),
    Table.Filter.Bool.fromNullable("video", "With video", EXTERNAL_EVENTS.VIDEOS_URL))
  private val EXTERNAL_EVENTS_SELECT = EXTERNAL_EVENTS.dropFields(_.name.startsWith("location_")).filters(FILTERS)
  private val ce = {
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
    internalEvents.union(externalEvents, alias = Some("e"), sorts = List(("start", "start", List("-start", "-created_at"))), search = List("name", "kind", "location", "twitter_account", "tags", "int_group_name", "int_cfp_name", "int_description", "ext_description"))
  }
  val COMMON_FILTERS = List(
    Table.Filter.Enum.fromValues("type", "Type", ce.kind, Event.Kind.all.map(k => (StringUtils.slugify(k.value), k.label, k))),
    Table.Filter.Bool.fromNullable("video", "With video", ce.ext_videos),
    new Table.Filter.Bool("past", "Is past", aggregation = false, ctx => ce.start.lt(ctx.now), ctx => ce.start.gt(ctx.now)))
  private val COMMON_EVENTS = ce.filters(COMMON_FILTERS)

  private[sql] def insert(e: ExternalEvent): Query.Insert[EXTERNAL_EVENTS] =
  // EXTERNAL_EVENTS.insert.values(e.id, e.name, e.kind, e.logo, e.description, e.start, e.finish, e.location, e.location.map(_.id), e.location.map(_.geo.lat), e.location.map(_.geo.lng), e.location.flatMap(_.locality), e.location.map(_.country), e.url, e.tickets, e.videos, e.twitterAccount, e.twitterHashtag, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    EXTERNAL_EVENTS.insert.values(fr0"${e.id}, ${e.name}, ${e.kind}, ${e.logo}, ${e.description}, ${e.start}, ${e.finish}, " ++ insertLocation(e.location) ++ fr0", ${e.url}, ${e.tickets}, ${e.videos}, ${e.twitterAccount}, ${e.twitterHashtag}, ${e.tags}, " ++ insertInfo(e.info))

  private[sql] def update(id: ExternalEvent.Id)(e: ExternalEvent.Data, by: User.Id, now: Instant): Query.Update[EXTERNAL_EVENTS] =
    EXTERNAL_EVENTS.update.set(_.NAME, e.name).set(_.KIND, e.kind).set(_.LOGO, e.logo).set(_.DESCRIPTION, e.description).set(_.START, e.start).set(_.FINISH, e.finish)
      .set(_.LOCATION, e.location).set(_.LOCATION_ID, e.location.map(_.id)).set(_.LOCATION_LAT, e.location.map(_.geo.lat)).set(_.LOCATION_LNG, e.location.map(_.geo.lng)).set(_.LOCATION_LOCALITY, e.location.flatMap(_.locality)).set(_.LOCATION_COUNTRY, e.location.map(_.country))
      .set(_.URL, e.url).set(_.TICKETS_URL, e.tickets).set(_.VIDEOS_URL, e.videos).set(_.TWITTER_ACCOUNT, e.twitterAccount).set(_.TWITTER_HASHTAG, e.twitterHashtag).set(_.TAGS, e.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(_.ID.is(id))

  private[sql] def selectOne(id: ExternalEvent.Id): Query.Select.Optional[ExternalEvent] =
    EXTERNAL_EVENTS_SELECT.select.where(_.id.is(id)).option[ExternalEvent](limit = true)

  private[sql] def selectAllIds()(implicit ctx: UserAwareCtx): Query.Select.All[ExternalEvent.Id] =
    EXTERNAL_EVENTS.select.withFields(_.ID).all[ExternalEvent.Id]

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[ExternalEvent] =
    EXTERNAL_EVENTS_SELECT.select.page[ExternalEvent](params.toSql, ctx.toSql)

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[CommonEvent] =
    COMMON_EVENTS.select.page[CommonEvent](params.toSql, ctx.toSql)

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
    EXTERNAL_EVENTS.select.withFields(_.TAGS).all[List[Tag]]

  private[sql] def selectLogos(): Query.Select.All[Option[Logo]] =
    EXTERNAL_EVENTS.select.withFields(_.LOGO).where(_.LOGO.notNull).all[Option[Logo]]
}
