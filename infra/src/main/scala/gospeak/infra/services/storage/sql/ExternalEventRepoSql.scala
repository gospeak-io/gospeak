package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{BasicCtx, Info, UserAwareCtx, UserCtx}
import gospeak.core.domain.{CommonEvent, Event, ExternalEvent, User}
import gospeak.core.services.storage.ExternalEventRepo
import gospeak.infra.services.storage.sql.ExternalEventRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, Logo, Page, Tag}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}

class ExternalEventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalEventRepo {
  override def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent] =
    insert(ExternalEvent(data, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def listAllIds()(implicit ctx: UserAwareCtx): IO[Seq[ExternalEvent.Id]] = selectAllIds().runList(xa)

  override def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[ExternalEvent]] = selectPage(params).run(xa)

  override def listCommon(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[CommonEvent]] = selectPageCommon(params).run(xa)

  override def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]] = selectOne(id).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def listLogos(): IO[Seq[Logo]] = selectLogos().runList(xa).map(_.flatten.distinct)
}

object ExternalEventRepoSql {

  import GenericQuery._

  private val _ = externalEventIdMeta // for intellij not remove DoobieUtils.Mappings import
  val table: Table = Tables.externalEvents.copy(filters = List(
    Table.Filter.Enum.fromEnum("type", "Type", "ee.kind", Seq(
      "conference" -> Event.Kind.Conference.value,
      "meetup" -> Event.Kind.Meetup.value,
      "training" -> Event.Kind.Training.value,
      "private event" -> Event.Kind.PrivateEvent.value)),
    Table.Filter.Bool.fromNullable("video", "With video", "ee.videos_url")))
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))
  val commonTable: Table = Table(
    name = "((SELECT e.name, e.kind, e.start, v.address as location, g.social_twitter as twitter_account, null as twitter_hashtag, e.tags, null as ext_id, null   as ext_logo, null          as ext_description, null  as ext_url, null          as ext_tickets, null         as ext_videos, e.id as int_id, e.slug as int_slug, e.description as int_description, g.id as int_group_id, g.slug as int_group_slug, g.name as int_group_name, g.logo as int_group_logo, c.id as int_cfp_id, c.slug as int_cfp_slug, c.name as int_cfp_name, v.id as int_venue_id, p.name as int_venue_name, p.logo as int_venue_logo, e.created_at, e.created_by, e.updated_at, e.updated_by FROM events e INNER JOIN groups g ON e.group_id=g.id LEFT OUTER JOIN cfps c ON e.cfp_id=c.id LEFT OUTER JOIN venues v ON e.venue=v.id LEFT OUTER JOIN partners p ON v.partner_id=p.id WHERE e.published IS NOT NULL) " +
      "UNION (SELECT e.name, e.kind, e.start,            e.location,                   e.twitter_account,       e.twitter_hashtag, e.tags, e.id as ext_id, e.logo as ext_logo, e.description as ext_description, e.url as ext_url, e.tickets_url as ext_tickets, e.videos_url as ext_videos, null as int_id, null   as int_slug, null          as int_description, null as int_group_id, null   as int_group_slug, null   as int_group_name, null   as int_group_logo, null as int_cfp_id, null   as int_cfp_slug, null   as int_cfp_name, null as int_venue_id, null   as int_venue_name, null   as int_venue_logo, e.created_at, e.created_by, e.updated_at, e.updated_by FROM external_events e))",
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
      Table.Filter.Enum.fromEnum("type", "Type", "e.kind", Seq(
        "conference" -> Event.Kind.Conference.value,
        "meetup" -> Event.Kind.Meetup.value,
        "training" -> Event.Kind.Training.value,
        "private event" -> Event.Kind.PrivateEvent.value)),
      Table.Filter.Bool.fromNullable("video", "With video", "e.ext_videos"),
      Table.Filter.Bool("past", "Is past", aggregation = false, ctx => fr0"e.start < ${ctx.now}", ctx => fr0"e.start > ${ctx.now}")))

  private[sql] def insert(e: ExternalEvent): Query.Insert[ExternalEvent] = {
    val values = fr0"${e.id}, ${e.name}, ${e.kind}, ${e.logo}, ${e.description}, ${e.start}, ${e.finish}, " ++ insertLocation(e.location) ++ fr0", ${e.url}, ${e.tickets}, ${e.videos}, ${e.twitterAccount}, ${e.twitterHashtag}, ${e.tags}, " ++ insertInfo(e.info)
    table.insert[ExternalEvent](e, _ => values)
  }

  private[sql] def update(id: ExternalEvent.Id)(e: ExternalEvent.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"name=${e.name}, kind=${e.kind}, logo=${e.logo}, description=${e.description}, start=${e.start}, finish=${e.finish}, " ++ updateLocation(e.location) ++ fr0", url=${e.url}, tickets_url=${e.tickets}, videos_url=${e.videos}, twitter_account=${e.twitterAccount}, twitter_hashtag=${e.twitterHashtag}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields).where(fr0"id=$id")
  }

  private[sql] def selectOne(id: ExternalEvent.Id): Query.Select[ExternalEvent] =
    tableSelect.select[ExternalEvent].where(fr0"ee.id=$id").one

  private[sql] def selectAllIds()(implicit ctx: UserAwareCtx): Query.Select[ExternalEvent.Id] =
    table.select[ExternalEvent.Id].fields(Field("id", "ee"))

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[ExternalEvent] =
    tableSelect.selectPage[ExternalEvent](params, adapt(ctx))

  private[sql] def selectPageCommon(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[CommonEvent] =
    commonTable.selectPage[CommonEvent](params, adapt(ctx))

  private[sql] def selectTags(): Query.Select[Seq[Tag]] =
    table.select[Seq[Tag]].fields(Field("tags", "ee"))

  private[sql] def selectLogos(): Query.Select[Option[Logo]] =
    table.select[Option[Logo]].fields(Field("logo", "ee")).where(fr0"ee.logo IS NOT NULL")

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
