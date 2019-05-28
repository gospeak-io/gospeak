package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
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

  override def find(group: Group.Id, pack: SponsorPack.Slug): IO[Option[SponsorPack]] = run(selectOne(group, pack).option)

  override def listAll(group: Group.Id): IO[Seq[SponsorPack]] = run(selectAll(group).to[List])
}

object SponsorPackRepoSql {
  private val _ = sponsorPackIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table: String = "sponsor_packs"
  private val fields: Seq[String] = Seq("id", "group_id", "slug", "name", "description", "price", "currency", "duration", "active", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id", "slug", "name", "description")
  private val defaultSort: Page.OrderBy = Page.OrderBy("name")

  private def values(e: SponsorPack): Fragment =
    fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.description}, ${e.price.amount}, ${e.price.currency}, ${e.duration}, ${e.active}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: SponsorPack): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, pack: SponsorPack.Slug)(data: SponsorPack.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, description=${data.description}, price=${data.price.amount}, currency=${data.price.currency}, duration=${data.duration}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, pack)).update
  }

  private[sql] def selectOne(group: Group.Id, pack: SponsorPack.Slug): doobie.Query0[SponsorPack] =
    buildSelect(tableFr, fieldsFr, where(group, pack)).query[SponsorPack]

  private[sql] def selectAll(group: Group.Id): doobie.Query0[SponsorPack] =
    buildSelect(tableFr, fieldsFr, where(group)).query[SponsorPack]

  private def where(group: Group.Id, slug: SponsorPack.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$slug"

  private def where(group: Group.Id): Fragment =
    fr0"WHERE group_id=$group"
}
