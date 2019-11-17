package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{CommonCfp, ExternalCfp, User}
import fr.gospeak.core.services.storage.ExternalCfpRepo
import fr.gospeak.infra.services.storage.sql.ExternalCfpRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Sorts, Table, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.domain.{Done, GMapPlace, Page, Tag}

class ExternalCfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalCfpRepo {
  override def create(data: ExternalCfp.Data, by: User.Id, now: Instant): IO[ExternalCfp] =
    insert(ExternalCfp(data, Info(by, now))).run(xa)

  override def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data, by: User.Id, now: Instant): IO[Done] =
    update(cfp)(data, by, now).run(xa)

  override def listOpen(now: Instant, params: Page.Params): IO[Page[CommonCfp]] = selectCommonPage(now, params).run(xa)

  override def find(cfp: ExternalCfp.Id): IO[Option[ExternalCfp]] = selectOne(cfp).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ExternalCfpRepoSql {
  private val _ = externalCfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalCfps
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))
  private val commonTable = Table(
    name = "((SELECT c.id,       c.slug, c.name, null as logo, c.begin, c.close, g.location, c.description, c.tags FROM cfps c INNER JOIN groups g ON c.group_id=g.id) " +
      "UNION (SELECT c.id, null as slug, c.name,       c.logo, c.begin, c.close, c.location, c.description, c.tags FROM external_cfps c))",
    prefix = "c",
    joins = Seq(),
    fields = Seq("id", "slug", "name", "logo", "begin", "close", "location", "description", "tags").map(Field(_, "c")),
    aggFields = Seq(),
    sorts = Sorts(Seq("close", "name").map(Field(_, "c")), Map()),
    search = Seq("name", "description", "tags").map(Field(_, "c")))

  private[sql] def insert(e: ExternalCfp): Insert[ExternalCfp] = {
    val values = fr0"${e.id}, ${e.name}, ${e.logo}, ${e.description}, ${e.begin}, ${e.close}, ${e.url}, ${e.event.start}, ${e.event.finish}, ${e.event.url}, " ++ insertLocation(e.event.location) ++ fr0", ${e.event.tickets}, ${e.event.videos}, ${e.event.twitterAccount}, ${e.event.twitterHashtag}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert[ExternalCfp](e, _ => values)
  }

  private[sql] def update(cfp: ExternalCfp.Id)(e: ExternalCfp.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"name=${e.name}, logo=${e.logo}, description=${e.description}, begin=${e.begin}, close=${e.close}, url=${e.url}, event_start=${e.event.start}, event_finish=${e.event.finish}, event_url=${e.event.url}, " ++ updateLocation(e.event.location) ++ fr0", tickets_url=${e.event.tickets}, videos_url=${e.event.videos}, twitter_account=${e.event.twitterAccount}, twitter_hashtag=${e.event.twitterHashtag}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE id=$cfp")
  }

  private[sql] def selectOne(cfp: ExternalCfp.Id): Select[ExternalCfp] =
    tableSelect.select[ExternalCfp](fr0"WHERE ec.id=$cfp")

  private[sql] def selectCommonPage(now: Instant, params: Page.Params): SelectPage[CommonCfp] =
    commonTable.selectPage(params, fr0"WHERE (c.begin IS NULL OR c.begin < $now) AND (c.close IS NULL OR c.close > $now)")

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ec")), Seq())

  private def insertLocation(a: Option[GMapPlace]) =
    fr0"$a, ${a.map(_.geo.lat)}, ${a.map(_.geo.lng)}, ${a.flatMap(_.locality)}, ${a.map(_.country)}"

  private def updateLocation(a: Option[GMapPlace]) =
    fr0"location=$a, location_lat=${a.map(_.geo.lat)}, location_lng=${a.map(_.geo.lng)}, location_locality=${a.flatMap(_.locality)}, location_country=${a.map(_.country)}"
}
