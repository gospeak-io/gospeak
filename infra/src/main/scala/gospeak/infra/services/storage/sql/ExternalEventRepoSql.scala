package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{Info, UserCtx}
import gospeak.core.domain.{CommonEvent, ExternalEvent, User}
import gospeak.core.services.storage.ExternalEventRepo
import gospeak.infra.services.storage.sql.ExternalEventRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Sorts, Table, Update}
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, Logo, Page, Tag}

class ExternalEventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalEventRepo {
  override def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent] =
    insert(ExternalEvent(data, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def list(params: Page.Params): IO[Page[ExternalEvent]] = selectPage(params).run(xa)

  override def listCommon(params: Page.Params): IO[Page[CommonEvent]] = selectPageCommon(params).run(xa)

  override def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]] = selectOne(id).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def listLogos(): IO[Seq[Logo]] = selectLogos().runList(xa).map(_.flatten.distinct)
}

object ExternalEventRepoSql {

  import GenericQuery._

  private val _ = externalEventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalEvents
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))
  private val commonTable = Table(
    name = "((SELECT e.id, e.name, e.kind, e.start, v.address as location, g.social_twitter as twitter_account, null as twitter_hashtag, e.tags, g.id as int_group_id, g.slug as int_group_slug, g.name as int_group_name, g.logo as int_group_logo, c.id as int_cfp_id, c.name as int_cfp_name, c.slug as int_cfp_slug, v.id as int_venue_id, p.logo as int_venue_logo, e.slug as int_event_slug, e.description as int_description, null   as ext_logo, null          as ext_description, null  as ext_url, e.created_at, e.created_by, e.updated_at, e.updated_by FROM events e INNER JOIN groups g ON e.group_id=g.id LEFT OUTER JOIN cfps c ON e.cfp_id=c.id LEFT OUTER JOIN venues v ON e.venue=v.id LEFT OUTER JOIN partners p ON v.partner_id=p.id WHERE e.published IS NOT NULL) " +
      "UNION (SELECT e.id, e.name, e.kind, e.start,            e.location,                   e.twitter_account,       e.twitter_hashtag, e.tags, null as int_group_id, null   as int_group_slug, null   as int_group_name, null   as int_group_logo, null as int_cfp_id, null   as int_cfp_name, null   as int_cfp_slug, null as int_venue_id, null   as int_venue_logo, null   as int_event_slug, null          as int_description, e.logo as ext_logo, e.description as ext_description, e.url as ext_url, e.created_at, e.created_by, e.updated_at, e.updated_by FROM external_events e))",
    prefix = "e",
    joins = Seq(),
    fields = Seq(
      "id", "name", "kind", "start", "location", "twitter_account", "twitter_hashtag", "tags",
      "int_group_id", "int_group_slug", "int_group_name", "int_group_logo", "int_cfp_id", "int_cfp_name", "int_cfp_slug", "int_venue_id", "int_venue_logo", "int_event_slug", "int_description",
      "ext_logo", "ext_description", "ext_url",
      "created_at", "created_by", "updated_at", "updated_by").map(Field(_, "e")),
    aggFields = Seq(),
    customFields = Seq(),
    sorts = Sorts(Seq("-created_at").map(Field(_, "e")), Map()),
    search = Seq("name", "kind", "location", "twitter_account", "tags", "int_group_name", "int_cfp_name", "int_description", "ext_description").map(Field(_, "e")))

  private[sql] def insert(e: ExternalEvent): Insert[ExternalEvent] = {
    val values = fr0"${e.id}, ${e.name}, ${e.kind}, ${e.logo}, ${e.description}, ${e.start}, ${e.finish}, " ++ insertLocation(e.location) ++ fr0", ${e.url}, ${e.tickets}, ${e.videos}, ${e.twitterAccount}, ${e.twitterHashtag}, ${e.tags}, " ++ insertInfo(e.info)
    table.insert[ExternalEvent](e, _ => values)
  }

  private[sql] def update(id: ExternalEvent.Id)(e: ExternalEvent.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"name=${e.name}, kind=${e.kind}, logo=${e.logo}, description=${e.description}, start=${e.start}, finish=${e.finish}, " ++ updateLocation(e.location) ++ fr0", url=${e.url}, tickets_url=${e.tickets}, videos_url=${e.videos}, twitter_account=${e.twitterAccount}, twitter_hashtag=${e.twitterHashtag}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE id=$id")
  }

  private[sql] def selectOne(id: ExternalEvent.Id): Select[ExternalEvent] =
    tableSelect.selectOne[ExternalEvent](fr0"WHERE ee.id=$id", Seq())

  private[sql] def selectPage(params: Page.Params): SelectPage[ExternalEvent] =
    tableSelect.selectPage[ExternalEvent](params)

  private[sql] def selectPageCommon(params: Page.Params): SelectPage[CommonEvent] =
    commonTable.selectPage[CommonEvent](params)

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ee")), Seq())

  private[sql] def selectLogos(): Select[Option[Logo]] =
    table.select[Option[Logo]](Seq(Field("logo", "ee")), fr0"WHERE ee.logo IS NOT NULL", Seq())
}
