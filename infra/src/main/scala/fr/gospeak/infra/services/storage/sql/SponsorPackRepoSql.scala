package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, SponsorPack, User}
import fr.gospeak.core.services.storage.SponsorPackRepo
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.{Insert, Select, Update}
import fr.gospeak.libs.scalautils.domain.{CustomException, Done}

class SponsorPackRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorPackRepo {
  override def create(group: Group.Id, data: SponsorPack.Data, by: User.Id, now: Instant): IO[SponsorPack] =
    insert(SponsorPack(group, data, Info(by, now))).run(xa)

  override def edit(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != pack) {
      find(group, data.slug).flatMap {
        case None => update(group, pack)(data, by, now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a sponsor pack with slug ${data.slug}"))
      }
    } else {
      update(group, pack)(data, by, now).run(xa)
    }
  }

  override def disable(group: Group.Id, pack: SponsorPack.Slug)(by: User.Id, now: Instant): IO[Done] =
    setActive(group, pack)(active = false, by, now).run(xa)

  override def enable(group: Group.Id, pack: SponsorPack.Slug)(by: User.Id, now: Instant): IO[Done] =
    setActive(group, pack)(active = true, by, now).run(xa)

  override def find(group: Group.Id, pack: SponsorPack.Slug): IO[Option[SponsorPack]] = selectOne(group, pack).runOption(xa)

  override def list(ids: Seq[SponsorPack.Id]): IO[Seq[SponsorPack]] = runNel(selectAll, ids)

  override def listAll(group: Group.Id): IO[Seq[SponsorPack]] = selectAll(group).runList(xa)

  override def listActives(group: Group.Id): IO[Seq[SponsorPack]] = selectActives(group).runList(xa)
}

object SponsorPackRepoSql {
  private val _ = sponsorPackIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.sponsorPacks

  private[sql] def insert(e: SponsorPack): Insert[SponsorPack] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.description}, ${e.price.amount}, ${e.price.currency}, ${e.duration}, ${e.active}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"sp.slug=${data.slug}, sp.name=${data.name}, sp.description=${data.description}, sp.price=${data.price.amount}, sp.currency=${data.price.currency}, sp.duration=${data.duration}, sp.updated=$now, sp.updated_by=$by"
    table.update(fields, where(group, pack))
  }

  private[sql] def setActive(group: Group.Id, pack: SponsorPack.Slug)(active: Boolean, by: User.Id, now: Instant): Update =
    table.update(fr0"sp.active=$active, sp.updated=$now, sp.updated_by=$by", where(group, pack))

  private[sql] def selectOne(group: Group.Id, pack: SponsorPack.Slug): Select[SponsorPack] =
    table.select[SponsorPack](where(group, pack))

  private[sql] def selectAll(ids: NonEmptyList[SponsorPack.Id]): Select[SponsorPack] =
    table.select[SponsorPack](fr"WHERE" ++ Fragments.in(fr"sp.id", ids))

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
