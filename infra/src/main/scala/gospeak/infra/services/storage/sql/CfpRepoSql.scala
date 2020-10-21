package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain._
import gospeak.core.domain.utils.{OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.services.storage.CfpRepo
import gospeak.infra.services.storage.sql.CfpRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{CFPS, EVENTS, PROPOSALS}
import gospeak.infra.services.storage.sql.database.tables.CFPS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.{CustomException, Page, Tag}
import gospeak.libs.sql.dsl.{Cond, Query}

class CfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CfpRepo {
  override def create(data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Cfp] = {
    val cfp = Cfp(ctx.group.id, data, ctx.info)
    insert(cfp).run(xa).map(_ => cfp)
  }

  override def edit(cfp: Cfp.Slug, data: Cfp.Data)(implicit ctx: OrgaCtx): IO[Unit] = {
    if (data.slug != cfp) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, cfp)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a cfp with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, cfp)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def find(id: Cfp.Id): IO[Option[Cfp]] = selectOne(id).run(xa)

  override def findRead(slug: Cfp.Slug): IO[Option[Cfp]] = selectOne(slug).run(xa)

  override def find(slug: Cfp.Slug)(implicit ctx: OrgaCtx): IO[Option[Cfp]] = selectOne(ctx.group.id, slug).run(xa)

  override def find(id: Event.Id): IO[Option[Cfp]] = selectOne(id).run(xa)

  override def findIncoming(slug: Cfp.Slug)(implicit ctx: UserAwareCtx): IO[Option[Cfp]] = selectOneIncoming(slug, ctx.now).run(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Cfp]] = selectPage(params).run(xa)

  override def availableFor(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Cfp]] = selectPage(talk, params).run(xa)

  override def list(group: Group.Id): IO[List[Cfp]] = selectAll(group).run(xa)

  override def list(ids: List[Cfp.Id]): IO[List[Cfp]] = runNel(selectAll, ids)

  override def listAllIncoming(group: Group.Id)(implicit ctx: UserAwareCtx): IO[List[Cfp]] = selectAllIncoming(group, ctx.now).run(xa)

  override def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[List[Cfp.Slug]] = selectAllPublicSlugs().run(xa)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)
}

object CfpRepoSql {
  private val CFPS_WITH_EVENTS = CFPS.joinOn(EVENTS.CFP_ID, _.Inner)

  private[sql] def insert(e: Cfp): Query.Insert[CFPS] =
  // CFPS.insert.values(e.id, e.group, e.slug, e.name, e.begin, e.close, e.description, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    CFPS.insert.values(fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.begin}, ${e.close}, ${e.description}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): Query.Update[CFPS] =
    CFPS.update.set(_.SLUG, data.slug).set(_.NAME, data.name).set(_.BEGIN, data.begin).set(_.CLOSE, data.close).set(_.DESCRIPTION, data.description).set(_.TAGS, data.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, slug))

  private[sql] def selectOne(id: Cfp.Id): Query.Select.Optional[Cfp] =
    CFPS.select.where(_.ID is id).option[Cfp]

  private[sql] def selectOne(slug: Cfp.Slug): Query.Select.Optional[Cfp] =
    CFPS.select.where(_.SLUG is slug).option[Cfp]

  private[sql] def selectOne(group: Group.Id, slug: Cfp.Slug): Query.Select.Optional[Cfp] =
    CFPS.select.where(where(group, slug)).option[Cfp]

  private[sql] def selectOne(id: Event.Id): Query.Select.Optional[Cfp] =
    CFPS_WITH_EVENTS.select.fields(CFPS.getFields).where(EVENTS.ID is id).option[Cfp]

  private[sql] def selectOneIncoming(slug: Cfp.Slug, now: Instant): Query.Select.Optional[Cfp] =
    CFPS.select.where(where(slug, now)).option[Cfp]

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Cfp] =
    CFPS.select.where(_.GROUP_ID is ctx.group.id).page[Cfp](params, ctx.toDb)

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Cfp] = {
    val TALK_CFPS = PROPOSALS.select.fields(PROPOSALS.CFP_ID).where(_.TALK_ID is talk).all[Cfp.Id]
    CFPS.select.where(_.ID notIn TALK_CFPS).page[Cfp](params, ctx.toDb)
  }

  private[sql] def selectAll(group: Group.Id): Query.Select.All[Cfp] =
    CFPS.select.where(_.GROUP_ID is group).all[Cfp]

  private[sql] def selectAll(ids: NonEmptyList[Cfp.Id]): Query.Select.All[Cfp] =
    CFPS.select.where(_.ID in ids).all[Cfp]

  private[sql] def selectAllPublicSlugs(): Query.Select.All[Cfp.Slug] =
    CFPS.select.fields(CFPS.SLUG).all[Cfp.Slug]

  private[sql] def selectAllIncoming(group: Group.Id, now: Instant): Query.Select.All[Cfp] =
    CFPS.select.where(where(group, now)).all[Cfp]

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
    CFPS.select.fields(CFPS.TAGS).all[List[Tag]]

  private def where(group: Group.Id, slug: Cfp.Slug): Cond = CFPS.GROUP_ID.is(group) and CFPS.SLUG.is(slug)

  private def where(now: Instant): Cond = (CFPS.CLOSE.isNull or CFPS.CLOSE.gt(TimeUtils.toLocalDateTime(now))).par

  private def where(slug: Cfp.Slug, now: Instant): Cond = where(now) and CFPS.SLUG.is(slug)

  private def where(group: Group.Id, now: Instant): Cond = where(now) and CFPS.GROUP_ID.is(group)
}
