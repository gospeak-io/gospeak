package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.domain.{Group, SponsorPack, User}
import gospeak.core.services.storage.SponsorPackRepo
import gospeak.infra.services.storage.sql.SponsorPackRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Insert, Select, Update}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{CustomException, Done}

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

  override def listAll(group: Group.Id): IO[Seq[SponsorPack]] = selectAll(group).runList(xa)

  override def listAll(implicit ctx: OrgaCtx): IO[Seq[SponsorPack]] = selectAll(ctx.group.id).runList(xa)

  override def listActives(group: Group.Id): IO[Seq[SponsorPack]] = selectActives(group).runList(xa)

  override def listActives(implicit ctx: OrgaCtx): IO[Seq[SponsorPack]] = selectActives(ctx.group.id).runList(xa)
}

object SponsorPackRepoSql {
  private val _ = sponsorPackIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.sponsorPacks

  private[sql] def insert(e: SponsorPack): Insert[SponsorPack] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.description}, ${e.price.amount}, ${e.price.currency}, ${e.duration}, ${e.active}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, description=${data.description}, price=${data.price.amount}, currency=${data.price.currency}, duration=${data.duration}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group, pack))
  }

  private[sql] def setActive(group: Group.Id, pack: SponsorPack.Slug)(active: Boolean, by: User.Id, now: Instant): Update =
    table.update(fr0"active=$active, updated_at=$now, updated_by=$by", where(group, pack))

  private[sql] def selectOne(group: Group.Id, pack: SponsorPack.Slug): Select[SponsorPack] =
    table.select[SponsorPack](where(group, pack))

  private[sql] def selectAll(ids: NonEmptyList[SponsorPack.Id]): Select[SponsorPack] =
    table.select[SponsorPack](fr0"WHERE " ++ Fragments.in(fr"sp.id", ids))

  private[sql] def selectAll(group: Group.Id): Select[SponsorPack] =
    table.select[SponsorPack](where(group))

  private[sql] def selectActives(group: Group.Id): Select[SponsorPack] = {
    val active = true
    table.select[SponsorPack](where(group) ++ fr0" AND sp.active=$active")
  }

  private def where(group: Group.Id, slug: SponsorPack.Slug): Fragment =
    fr0"WHERE sp.group_id=$group AND sp.slug=$slug"

  private def where(group: Group.Id): Fragment =
    fr0"WHERE sp.group_id=$group"
}
