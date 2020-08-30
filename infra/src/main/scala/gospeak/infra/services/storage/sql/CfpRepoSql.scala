package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.CfpRepo
import gospeak.infra.services.storage.sql.CfpRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Done, Page, Tag}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query}

class CfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CfpRepo {
  override def create(data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Cfp] =
    insert(Cfp(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(cfp: Cfp.Slug, data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Done] = {
    if (data.slug != cfp) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, cfp)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a cfp with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, cfp)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def find(id: Cfp.Id): IO[Option[Cfp]] = selectOne(id).runOption(xa)

  override def findRead(slug: Cfp.Slug): IO[Option[Cfp]] = selectOne(slug).runOption(xa)

  override def find(slug: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Option[Cfp]] = selectOne(ctx.group.id, slug).runOption(xa)

  override def find(id: Event.Id): IO[Option[Cfp]] = selectOne(id).runOption(xa)

  override def findIncoming(slug: Cfp.Slug)(implicit ctx: UserAwareCtx): IO[Option[Cfp]] = selectOneIncoming(slug, ctx.now).runOption(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Cfp]] = selectPage(params).run(xa)

  override def availableFor(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Cfp]] = selectPage(talk, params).run(xa)

  override def list(ids: List[Cfp.Id]): IO[List[Cfp]] = runNel(selectAll, ids)

  override def list(group: Group.Id): IO[List[Cfp]] = selectAll(group).runList(xa)

  override def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[List[Cfp.Slug]] = selectAllPublicSlugs().runList(xa)

  override def listAllIncoming(group: Group.Id)(implicit ctx: UserAwareCtx): IO[List[Cfp]] = selectAllIncoming(group, ctx.now).runList(xa)

  override def listTags(): IO[List[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object CfpRepoSql {
  private val _ = cfpIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.cfps
  private val tableWithEvent = table.join(Tables.events, _.id -> _.cfp_id).get

  private[sql] def insert(e: Cfp): Query.Insert[Cfp] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.begin}, ${e.close}, ${e.description}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Cfp](e, _ => values)
  }

  private[sql] def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, begin=${data.begin}, close=${data.close}, description=${data.description}, tags=${data.tags}, updated_at=$now, updated_by=$by"
    table.update(fields).where(where(group, slug))
  }

  private[sql] def selectOne(id: Cfp.Id): Query.Select[Cfp] =
    table.select[Cfp].where(fr0"c.id=$id")

  private[sql] def selectOne(slug: Cfp.Slug): Query.Select[Cfp] =
    table.select[Cfp].where(fr0"c.slug=$slug")

  private[sql] def selectOne(group: Group.Id, slug: Cfp.Slug): Query.Select[Cfp] =
    table.select[Cfp].where(where(group, slug))

  private[sql] def selectOne(id: Event.Id): Query.Select[Cfp] =
    tableWithEvent.select[Cfp].fields(Tables.cfps.fields).where(fr0"e.id=$id")

  private[sql] def selectOneIncoming(slug: Cfp.Slug, now: Instant): Query.Select[Cfp] =
    table.select[Cfp].where(where(slug, now))

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Cfp] =
    table.selectPage[Cfp](params, adapt(ctx)).where(fr0"c.group_id=${ctx.group.id}")

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Cfp] = {
    val talkCfps = Tables.proposals.select[Cfp.Id].fields(Field("cfp_id", "p")).where(fr0"p.talk_id=$talk")
    table.selectPage[Cfp](params, adapt(ctx)).where(fr0"c.id NOT IN (" ++ talkCfps.fr ++ fr0")")
  }

  private[sql] def selectAll(group: Group.Id): Query.Select[Cfp] =
    table.select[Cfp].where(fr0"c.group_id=$group")

  private[sql] def selectAll(ids: NonEmptyList[Cfp.Id]): Query.Select[Cfp] =
    table.select[Cfp].where(Fragments.in(fr"c.id", ids))

  private[sql] def selectAllPublicSlugs(): Query.Select[Cfp.Slug] =
    table.select[Cfp.Slug].fields(Field("slug", "c"))

  private[sql] def selectAllIncoming(group: Group.Id, now: Instant): Query.Select[Cfp] =
    table.select[Cfp].where(where(group, now))

  private[sql] def selectTags(): Query.Select[List[Tag]] =
    table.select[List[Tag]].fields(Field("tags", "c"))

  private def where(group: Group.Id, slug: Cfp.Slug): Fragment =
    fr0"c.group_id=$group AND c.slug=$slug"

  private def where(now: Instant): Fragment =
    fr0"(c.close IS NULL OR c.close > $now)"

  private def where(slug: Cfp.Slug, now: Instant): Fragment =
    where(now) ++ fr0" AND c.slug=$slug"

  private def where(group: Group.Id, now: Instant): Fragment =
    where(now) ++ fr0" AND c.group_id=$group"

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
