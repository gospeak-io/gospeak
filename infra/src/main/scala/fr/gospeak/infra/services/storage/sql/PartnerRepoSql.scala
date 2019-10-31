package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, Partner, User}
import fr.gospeak.core.services.storage.PartnerRepo
import fr.gospeak.infra.services.storage.sql.PartnerRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Insert, Select, SelectPage, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page}

class PartnerRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with PartnerRepo {
  override def create(group: Group.Id, data: Partner.Data, by: User.Id, now: Instant): IO[Partner] =
    insert(Partner(group, data, Info(by, now))).run(xa)

  override def edit(group: Group.Id, partner: Partner.Slug)(data: Partner.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != partner) {
      find(group, data.slug).flatMap {
        case None => update(group, partner)(data, by, now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      update(group, partner)(data, by, now).run(xa)
    }
  }

  override def remove(group: Group.Id, partner: Partner.Slug)(by: User.Id, now: Instant): IO[Done] = delete(group, partner).run(xa)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Partner]] = selectPage(group, params).run(xa)

  override def list(group: Group.Id): IO[Seq[Partner]] = selectAll(group).runList(xa)

  override def list(partners: Seq[Partner.Id]): IO[Seq[Partner]] = runNel(selectAll, partners)

  override def find(group: Group.Id, partner: Partner.Id): IO[Option[Partner]] = selectOne(group, partner).runOption(xa)

  override def find(group: Group.Id, partner: Partner.Slug): IO[Option[Partner]] = selectOne(group, partner).runOption(xa)
}

object PartnerRepoSql {
  private val _ = partnerIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.partners

  private[sql] def insert(e: Partner): Insert[Partner] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.notes}, ${e.description}, ${e.logo}, ${e.twitter}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert[Partner](e, _ => values)
  }

  private[sql] def update(group: Group.Id, partner: Partner.Slug)(data: Partner.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, notes=${data.notes}, description=${data.description}, logo=${data.logo}, twitter=${data.twitter}, updated=$now, updated_by=$by"
    table.update(fields, where(group, partner))
  }

  private[sql] def delete(group: Group.Id, partner: Partner.Slug): Delete =
    table.delete(where(group, partner))

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Partner] =
    table.selectPage[Partner](params, fr0"WHERE pa.group_id=$group")

  private[sql] def selectAll(group: Group.Id): Select[Partner] =
    table.select[Partner](fr0"WHERE pa.group_id=$group")

  private[sql] def selectAll(ids: NonEmptyList[Partner.Id]): Select[Partner] =
    table.select[Partner](fr0"WHERE " ++ Fragments.in(fr"pa.id", ids))

  private[sql] def selectOne(group: Group.Id, partner: Partner.Id): Select[Partner] =
    table.select[Partner](where(group, partner))

  private[sql] def selectOne(group: Group.Id, partner: Partner.Slug): Select[Partner] =
    table.select[Partner](where(group, partner))

  private def where(group: Group.Id, partner: Partner.Id): Fragment =
    fr0"WHERE pa.group_id=$group AND pa.id=$partner"

  private def where(group: Group.Id, partner: Partner.Slug): Fragment =
    fr0"WHERE pa.group_id=$group AND pa.slug=$partner"
}
