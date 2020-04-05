package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.CfpRepo
import gospeak.infra.services.storage.sql.CfpRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Done, Page, Tag}

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

  override def list(ids: Seq[Cfp.Id]): IO[Seq[Cfp]] = runNel(selectAll, ids)

  override def list(group: Group.Id): IO[Seq[Cfp]] = selectAll(group).runList(xa)

  override def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[Seq[Cfp.Slug]] = selectAllPublicSlugs().runList(xa)

  override def listAllIncoming(group: Group.Id)(implicit ctx: UserAwareCtx): IO[Seq[Cfp]] = selectAllIncoming(group, ctx.now).runList(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object CfpRepoSql {
  private val _ = cfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.cfps
  private val tableWithEvent = table.join(Tables.events, _.id -> _.cfp_id).get

  private[sql] def insert(e: Cfp): Insert[Cfp] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.begin}, ${e.close}, ${e.description}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Cfp](e, _ => values)
  }

  private[sql] def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, begin=${data.begin}, close=${data.close}, description=${data.description}, tags=${data.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group, slug))
  }

  private[sql] def selectOne(id: Cfp.Id): Select[Cfp] =
    table.select[Cfp](fr0"WHERE c.id=$id")

  private[sql] def selectOne(slug: Cfp.Slug): Select[Cfp] =
    table.select[Cfp](fr0"WHERE c.slug=$slug")

  private[sql] def selectOne(group: Group.Id, slug: Cfp.Slug): Select[Cfp] =
    table.select[Cfp](where(group, slug))

  private[sql] def selectOne(id: Event.Id): Select[Cfp] =
    tableWithEvent.select[Cfp](Tables.cfps.fields, fr0"WHERE e.id=$id")

  private[sql] def selectOneIncoming(slug: Cfp.Slug, now: Instant): Select[Cfp] =
    table.select[Cfp](where(slug, now))

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): SelectPage[Cfp, OrgaCtx] =
    table.selectPage[Cfp, OrgaCtx](params, fr0"WHERE c.group_id=${ctx.group.id}")

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): SelectPage[Cfp, UserCtx] = {
    val talkCfps = Tables.proposals.select[Cfp.Id](Seq(Field("cfp_id", "p")), fr0"WHERE p.talk_id=$talk")
    table.selectPage[Cfp, UserCtx](params, fr0"WHERE c.id NOT IN (" ++ talkCfps.fr ++ fr0")")
  }

  private[sql] def selectAll(group: Group.Id): Select[Cfp] =
    table.select[Cfp](fr0"WHERE c.group_id=$group")

  private[sql] def selectAll(ids: NonEmptyList[Cfp.Id]): Select[Cfp] =
    table.select[Cfp](fr0"WHERE " ++ Fragments.in(fr"c.id", ids))

  private[sql] def selectAllPublicSlugs(): Select[Cfp.Slug] =
    table.select[Cfp.Slug](Seq(Field("slug", "c")))

  private[sql] def selectAllIncoming(group: Group.Id, now: Instant): Select[Cfp] =
    table.select[Cfp](where(group, now))

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "c")))

  private def where(group: Group.Id, slug: Cfp.Slug): Fragment =
    fr0"WHERE c.group_id=$group AND c.slug=$slug"

  private def where(now: Instant): Fragment =
    fr0"WHERE (c.close IS NULL OR c.close > $now)"

  private def where(slug: Cfp.Slug, now: Instant): Fragment =
    where(now) ++ fr0" AND c.slug=$slug"

  private def where(group: Group.Id, now: Instant): Fragment =
    where(now) ++ fr0" AND c.group_id=$group"
}
