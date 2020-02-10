package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain.utils.{Info, UserCtx}
import gospeak.core.domain.{ExternalEvent, User}
import gospeak.core.services.storage.ExternalEventRepo
import gospeak.infra.services.storage.sql.ExternalEventRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, Logo, Page, Tag}

class ExternalEventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalEventRepo {
  override def create(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[ExternalEvent] =
    insert(ExternalEvent(data, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalEvent.Id)(data: ExternalEvent.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def list(params: Page.Params): IO[Page[ExternalEvent]] = selectPage(params).run(xa)

  override def find(id: ExternalEvent.Id): IO[Option[ExternalEvent]] = selectOne(id).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)

  override def listLogos(): IO[Seq[Logo]] = selectLogos().runList(xa).map(_.flatten.distinct)
}

object ExternalEventRepoSql {

  import GenericQuery._

  private val _ = externalEventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalEvents
  private val tableSelect = table.dropFields(_.name.startsWith("location_"))

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

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ee")), Seq())

  private[sql] def selectLogos(): Select[Option[Logo]] =
    table.select[Option[Logo]](Seq(Field("logo", "ee")), fr0"WHERE ee.logo IS NOT NULL", Seq())
}
