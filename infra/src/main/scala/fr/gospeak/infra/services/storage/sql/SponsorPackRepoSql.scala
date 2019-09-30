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
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page}

class SponsorPackRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorPackRepo {
  override def create(group: Group.Id, data: SponsorPack.Data, by: User.Id, now: Instant): IO[SponsorPack] =
    run(insert, SponsorPack(group, data, Info(by, now)))

  override def edit(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != pack) {
      find(group, data.slug).flatMap {
        case None => run(update(group, pack)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have a sponsor pack with slug ${data.slug}"))
      }
    } else {
      run(update(group, pack)(data, by, now))
    }
  }

  override def disable(group: Group.Id, pack: SponsorPack.Slug)(by: User.Id, now: Instant): IO[Done] =
    run(SponsorPackRepoSql.setActive(group, pack)(active = false, by, now))

  override def enable(group: Group.Id, pack: SponsorPack.Slug)(by: User.Id, now: Instant): IO[Done] =
    run(SponsorPackRepoSql.setActive(group, pack)(active = true, by, now))

  override def find(group: Group.Id, pack: SponsorPack.Slug): IO[Option[SponsorPack]] = run(selectOne(group, pack).option)

  override def list(ids: Seq[SponsorPack.Id]): IO[Seq[SponsorPack]] = runIn(selectAll)(ids)

  override def listAll(group: Group.Id): IO[Seq[SponsorPack]] = run(selectAll(group).to[List])

  override def listActives(group: Group.Id): IO[Seq[SponsorPack]] = run(selectActives(group).to[List])
}

object SponsorPackRepoSql {
  private val _ = sponsorPackIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table: String = "sponsor_packs"
  private[sql] val fields: Seq[String] = Seq("id", "group_id", "slug", "name", "description", "price", "currency", "duration", "active", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private[sql] val searchFields: Seq[String] = Seq("id", "slug", "name", "description")
  private val defaultSort: Page.OrderBy = Page.OrderBy("name")

  private def values(e: SponsorPack): Fragment =
    fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.description}, ${e.price.amount}, ${e.price.currency}, ${e.duration}, ${e.active}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: SponsorPack): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, description=${data.description}, price=${data.price.amount}, currency=${data.price.currency}, duration=${data.duration}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, pack)).update
  }

  private[sql] def setActive(group: Group.Id, pack: SponsorPack.Slug)(active: Boolean, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"active=$active, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, pack)).update
  }

  private[sql] def selectOne(group: Group.Id, pack: SponsorPack.Slug): doobie.Query0[SponsorPack] =
    buildSelect(tableFr, fieldsFr, where(group, pack)).query[SponsorPack]

  private[sql] def selectAll(ids: NonEmptyList[SponsorPack.Id]): doobie.Query0[SponsorPack] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[SponsorPack]

  private[sql] def selectAll(group: Group.Id): doobie.Query0[SponsorPack] =
    buildSelect(tableFr, fieldsFr, where(group)).query[SponsorPack]

  private[sql] def selectActives(group: Group.Id): doobie.Query0[SponsorPack] = {
    val active = true
    buildSelect(tableFr, fieldsFr, where(group) ++ fr0" AND active=$active").query[SponsorPack]
  }

  private def where(group: Group.Id, slug: SponsorPack.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$slug"

  private def where(group: Group.Id): Fragment =
    fr0"WHERE group_id=$group"
}
