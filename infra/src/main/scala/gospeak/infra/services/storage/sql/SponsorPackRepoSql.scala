package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.domain.{Group, SponsorPack, User}
import gospeak.core.services.storage.SponsorPackRepo
import gospeak.infra.services.storage.sql.SponsorPackRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.SPONSOR_PACKS
import gospeak.infra.services.storage.sql.database.tables.SPONSOR_PACKS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.CustomException
import gospeak.libs.sql.dsl.{Cond, Query}

class SponsorPackRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorPackRepo {
  override def create(data: SponsorPack.Data)(implicit ctx: OrgaCtx): IO[SponsorPack] = {
    val sponsorPack = SponsorPack(ctx.group.id, data, ctx.info)
    insert(sponsorPack).run(xa).map(_ => sponsorPack)
  }

  override def edit(pack: SponsorPack.Slug, data: SponsorPack.Data)(implicit ctx: OrgaCtx): IO[Unit] = {
    if (data.slug != pack) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, pack)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a sponsor pack with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, pack)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def disable(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Unit] =
    setActive(ctx.group.id, pack)(active = false, ctx.user.id, ctx.now).run(xa)

  override def enable(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Unit] =
    setActive(ctx.group.id, pack)(active = true, ctx.user.id, ctx.now).run(xa)

  override def find(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Option[SponsorPack]] = selectOne(ctx.group.id, pack).run(xa)

  override def listAll(group: Group.Id): IO[List[SponsorPack]] = selectAll(group).run(xa)

  override def listAll(implicit ctx: OrgaCtx): IO[List[SponsorPack]] = selectAll(ctx.group.id).run(xa)

  override def listActives(group: Group.Id): IO[List[SponsorPack]] = selectActives(group).run(xa)

  override def listActives(implicit ctx: OrgaCtx): IO[List[SponsorPack]] = selectActives(ctx.group.id).run(xa)
}

object SponsorPackRepoSql {
  private[sql] def insert(e: SponsorPack): Query.Insert[SPONSOR_PACKS] =
    SPONSOR_PACKS.insert.values(e.id, e.group, e.slug, e.name, e.description, e.price.amount, e.price.currency, e.duration, e.active, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)

  private[sql] def update(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): Query.Update[SPONSOR_PACKS] =
    SPONSOR_PACKS.update.set(_.SLUG, data.slug).set(_.NAME, data.name).set(_.DESCRIPTION, data.description).set(_.PRICE, data.price.amount).set(_.CURRENCY, data.price.currency).set(_.DURATION, data.duration).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, pack))

  private[sql] def setActive(group: Group.Id, pack: SponsorPack.Slug)(active: Boolean, by: User.Id, now: Instant): Query.Update[SPONSOR_PACKS] =
    SPONSOR_PACKS.update.set(_.ACTIVE, active).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, pack))

  private[sql] def selectOne(group: Group.Id, pack: SponsorPack.Slug): Query.Select.Optional[SponsorPack] =
    SPONSOR_PACKS.select.where(where(group, pack)).option[SponsorPack]

  private[sql] def selectAll(ids: NonEmptyList[SponsorPack.Id]): Query.Select.All[SponsorPack] =
    SPONSOR_PACKS.select.where(_.ID in ids).all[SponsorPack]

  private[sql] def selectAll(group: Group.Id): Query.Select.All[SponsorPack] =
    SPONSOR_PACKS.select.where(where(group)).all[SponsorPack]

  private[sql] def selectActives(group: Group.Id): Query.Select.All[SponsorPack] =
    SPONSOR_PACKS.select.where(sp => sp.GROUP_ID.is(group) and sp.ACTIVE.is(true)).all[SponsorPack]

  private def where(group: Group.Id, slug: SponsorPack.Slug): Cond = SPONSOR_PACKS.GROUP_ID.is(group) and SPONSOR_PACKS.SLUG.is(slug)

  private def where(group: Group.Id): Cond = SPONSOR_PACKS.GROUP_ID is group
}
