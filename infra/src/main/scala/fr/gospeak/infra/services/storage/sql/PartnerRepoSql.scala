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
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page}

class PartnerRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with PartnerRepo {
  override def create(group: Group.Id, data: Partner.Data, by: User.Id, now: Instant): IO[Partner] =
    run(insert, Partner(group, data, Info(by, now)))

  override def edit(group: Group.Id, slug: Partner.Slug)(data: Partner.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != slug) {
      find(group, data.slug).flatMap {
        case None => run(update(group, slug)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      run(update(group, slug)(data, by, now))
    }
  }

  override def list(group: Group.Id, params: Page.Params): IO[Page[Partner]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(group: Group.Id): IO[Seq[Partner]] = run(selectAll(group).to[List])

  override def list(ids: Seq[Partner.Id]): IO[Seq[Partner]] = runIn(selectAll)(ids)

  override def find(group: Group.Id, slug: Partner.Slug): IO[Option[Partner]] = run(selectOne(group, slug).option)
}

object PartnerRepoSql {
  private val _ = partnerIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "partners"
  private[sql] val fields = Seq("id", "group_id", "slug", "name", "description", "logo", "twitter", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description")
  private val defaultSort = Page.OrderBy("name")

  private def values(e: Partner): Fragment =
    fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.description}, ${e.logo}, ${e.twitter}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Partner): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, slug: Partner.Slug)(data: Partner.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, description=${data.description}, logo=${data.logo}, twitter=${data.twitter}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, slug)).update
  }

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Partner], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Partner], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(group: Group.Id): doobie.Query0[Partner] =
    buildSelect(tableFr, fieldsFr, fr"WHERE group_id=$group").query[Partner]

  private[sql] def selectAll(ids: NonEmptyList[Partner.Id]): doobie.Query0[Partner] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Partner]

  private[sql] def selectOne(group: Group.Id, slug: Partner.Slug): doobie.Query0[Partner] =
    buildSelect(tableFr, fieldsFr, where(group, slug)).query[Partner]

  private def where(group: Group.Id, slug: Partner.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$slug"
}
