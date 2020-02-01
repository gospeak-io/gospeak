package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{Info, UserCtx}
import gospeak.core.domain.{Cfp, CommonCfp, ExternalCfp, User}
import gospeak.core.services.storage.ExternalCfpRepo
import gospeak.infra.services.storage.sql.ExternalCfpRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Sorts, Table, Update}
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, Page, Tag}

class ExternalCfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalCfpRepo {
  override def create(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[ExternalCfp] =
    insert(ExternalCfp(data, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(cfp: ExternalCfp.Id)(data: ExternalCfp.Data)(implicit ctx: UserCtx): IO[Done] =
    update(cfp)(data, ctx.user.id, ctx.now).run(xa)

  override def listIncoming(now: Instant, params: Page.Params): IO[Page[CommonCfp]] = selectCommonPageIncoming(now, params).run(xa)

  override def listDuplicates(p: ExternalCfp.DuplicateParams): IO[Seq[ExternalCfp]] = selectDuplicates(p).runList(xa)

  override def find(cfp: ExternalCfp.Id): IO[Option[ExternalCfp]] = selectOne(cfp).runOption(xa)

  override def findCommon(cfp: Cfp.Slug): IO[Option[CommonCfp]] = selectOneCommon(cfp).runOption(xa)

  override def findCommon(cfp: ExternalCfp.Id): IO[Option[CommonCfp]] = selectOneCommon(cfp).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ExternalCfpRepoSql {
  import GenericQuery._
  private val _ = externalCfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalCfps
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))
  private val commonTable = Table(
    name = "((SELECT c.id,       c.slug, c.name, g.logo, null as url, c.begin, c.close, g.location, c.description, null as event_start, null as event_finish, null as event_url, null as tickets_url, null as videos_url, null as twitter_account, null as twitter_hashtag, c.tags, g.id as group_id, g.slug as group_slug FROM cfps c INNER JOIN groups g ON c.group_id=g.id) " +
      "UNION (SELECT c.id, null as slug, c.name, c.logo,       c.url, c.begin, c.close, c.location, c.description,       c.event_start,       c.event_finish,       c.event_url,       c.tickets_url,       c.videos_url,       c.twitter_account,       c.twitter_hashtag, c.tags, null as group_id,   null as group_slug FROM external_cfps c))",
    prefix = "c",
    joins = Seq(),
    fields = Seq("id", "slug", "name", "logo", "url", "begin", "close", "location", "description", "event_start", "event_finish", "event_url", "tickets_url", "videos_url", "twitter_account", "twitter_hashtag", "tags", "group_id", "group_slug").map(Field(_, "c")),
    aggFields = Seq(),
    customFields = Seq(),
    sorts = Sorts(Seq("close", "name").map(Field(_, "c")), Map()),
    search = Seq("name", "description", "tags").map(Field(_, "c")))

  private[sql] def insert(e: ExternalCfp): Insert[ExternalCfp] = {
    val values = fr0"${e.id}, ${e.name}, ${e.logo}, ${e.description}, ${e.begin}, ${e.close}, ${e.url}, ${e.event.start}, ${e.event.finish}, ${e.event.url}, " ++ insertLocation(e.event.location) ++ fr0", ${e.event.tickets}, ${e.event.videos}, ${e.event.twitterAccount}, ${e.event.twitterHashtag}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[ExternalCfp](e, _ => values)
  }

  private[sql] def update(id: ExternalCfp.Id)(e: ExternalCfp.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"name=${e.name}, logo=${e.logo}, description=${e.description}, begin=${e.begin}, close=${e.close}, url=${e.url}, event_start=${e.event.start}, event_finish=${e.event.finish}, event_url=${e.event.url}, " ++ updateLocation(e.event.location) ++ fr0", tickets_url=${e.event.tickets}, videos_url=${e.event.videos}, twitter_account=${e.event.twitterAccount}, twitter_hashtag=${e.event.twitterHashtag}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE id=$id")
  }

  private[sql] def selectOne(id: ExternalCfp.Id): Select[ExternalCfp] =
    tableSelect.selectOne[ExternalCfp](fr0"WHERE ec.id=$id", Seq())

  private[sql] def selectOneCommon(slug: Cfp.Slug): Select[CommonCfp] =
    commonTable.selectOne[CommonCfp](fr0"WHERE c.slug=$slug", Seq())

  private[sql] def selectOneCommon(id: ExternalCfp.Id): Select[CommonCfp] =
    commonTable.selectOne[CommonCfp](fr0"WHERE c.id=$id", Seq())

  private[sql] def selectCommonPageIncoming(now: Instant, params: Page.Params): SelectPage[CommonCfp] =
    commonTable.selectPage[CommonCfp](params, fr0"WHERE (c.close IS NULL OR c.close >= $now)")

  private[sql] def selectDuplicates(p: ExternalCfp.DuplicateParams): Select[ExternalCfp] = {
    val filters = Seq(
      p.cfpUrl.map(v => fr0"ec.url LIKE ${"%" + v + "%"}"),
      p.cfpName.map(v => fr0"ec.name LIKE ${"%" + v + "%"}"),
      p.cfpEndDate.map(v => fr0"ec.close=$v"),
      p.eventUrl.map(v => fr0"ec.event_url LIKE ${"%" + v + "%"}"),
      p.eventStartDate.map(v => fr0"ec.event_start=$v"),
      p.twitterAccount.map(v => fr0"ec.twitter_account LIKE ${"%" + v + "%"}"),
      p.twitterHashtag.map(v => fr0"ec.twitter_hashtag LIKE ${"%" + v + "%"}")
    ).flatten
    if (filters.isEmpty) {
      tableSelect.select[ExternalCfp](fr0"WHERE ec.id='no-match'")
    } else {
      tableSelect.select[ExternalCfp](fr0"WHERE " ++ filters.reduce(_ ++ fr0" OR " ++ _))
    }
  }

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ec")), Seq())
}
