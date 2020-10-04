package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.syntax.string._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.domain.{Group, SponsorPack, User}
import gospeak.core.services.storage.SponsorPackRepo
import gospeak.infra.services.storage.sql.SponsorPackRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.SPONSOR_PACKS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{CustomException, Done}
import gospeak.libs.sql.doobie.Query
import gospeak.libs.sql.dsl.Cond

class SponsorPackRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorPackRepo {
  override def create(data: SponsorPack.Data)(implicit ctx: OrgaCtx): IO[SponsorPack] =
    insert(SponsorPack(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(pack: SponsorPack.Slug, data: SponsorPack.Data)(implicit ctx: OrgaCtx): IO[Done] = {
    if (data.slug != pack) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, pack)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a sponsor pack with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, pack)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def disable(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Done] =
    setActive(ctx.group.id, pack)(active = false, ctx.user.id, ctx.now).run(xa)

  override def enable(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Done] =
    setActive(ctx.group.id, pack)(active = true, ctx.user.id, ctx.now).run(xa)

  override def find(pack: SponsorPack.Slug)(implicit ctx: OrgaCtx): IO[Option[SponsorPack]] = selectOne(ctx.group.id, pack).runOption(xa)

  override def listAll(group: Group.Id): IO[List[SponsorPack]] = selectAll(group).runList(xa)

  override def listAll(implicit ctx: OrgaCtx): IO[List[SponsorPack]] = selectAll(ctx.group.id).runList(xa)

  override def listActives(group: Group.Id): IO[List[SponsorPack]] = selectActives(group).runList(xa)

  override def listActives(implicit ctx: OrgaCtx): IO[List[SponsorPack]] = selectActives(ctx.group.id).runList(xa)
}

object SponsorPackRepoSql {
  private val _ = sponsorPackIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.sponsorPacks

  private[sql] def insert(e: SponsorPack): Query.Insert[SponsorPack] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.description}, ${e.price.amount}, ${e.price.currency}, ${e.duration}, ${e.active}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[SponsorPack](e, _ => values)
    val q2 = SPONSOR_PACKS.insert.values(e.id, e.group, e.slug, e.name, e.description, e.price.amount, e.price.currency, e.duration, e.active, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, description=${data.description}, price=${data.price.amount}, currency=${data.price.currency}, duration=${data.duration}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(where(group, pack))
    val q2 = SPONSOR_PACKS.update.set(_.SLUG, data.slug).set(_.NAME, data.name).set(_.DESCRIPTION, data.description).set(_.PRICE, data.price.amount).set(_.CURRENCY, data.price.currency).set(_.DURATION, data.duration).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, pack))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def setActive(group: Group.Id, pack: SponsorPack.Slug)(active: Boolean, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"active=$active, updated_at=$now, updated_by=$by").where(where(group, pack))
    val q2 = SPONSOR_PACKS.update.set(_.ACTIVE, active).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, pack))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(group: Group.Id, pack: SponsorPack.Slug): Query.Select[SponsorPack] = {
    val q1 = table.select[SponsorPack].where(where(group, pack))
    val q2 = SPONSOR_PACKS.select.where(where2(group, pack)).option[SponsorPack]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(ids: NonEmptyList[SponsorPack.Id]): Query.Select[SponsorPack] = {
    val q1 = table.select[SponsorPack].where(Fragments.in(fr"sp.id", ids))
    val q2 = SPONSOR_PACKS.select.where(_.ID in ids).all[SponsorPack]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(group: Group.Id): Query.Select[SponsorPack] = {
    val q1 = table.select[SponsorPack].where(where(group))
    val q2 = SPONSOR_PACKS.select.where(where2(group)).all[SponsorPack]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectActives(group: Group.Id): Query.Select[SponsorPack] = {
    val active = true
    val q1 = table.select[SponsorPack].where(where(group) ++ fr0" AND sp.active=$active")
    val q2 = SPONSOR_PACKS.select.where(sp => sp.GROUP_ID.is(group) and sp.ACTIVE.is(active)).all[SponsorPack]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(group: Group.Id, slug: SponsorPack.Slug): Fragment = fr0"sp.group_id=$group AND sp.slug=$slug"

  private def where2(group: Group.Id, slug: SponsorPack.Slug): Cond = SPONSOR_PACKS.GROUP_ID.is(group) and SPONSOR_PACKS.SLUG.is(slug)

  private def where(group: Group.Id): Fragment = fr0"sp.group_id=$group"

  private def where2(group: Group.Id): Cond = SPONSOR_PACKS.GROUP_ID is group
}
