package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.CfpRepo
import fr.gospeak.infra.services.storage.sql.CfpRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments.{buildInsert, buildSelect, buildUpdate, paginate}
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}

class CfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CfpRepo {
  override def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp] =
    run(insert, Cfp(group, data, Info(by, now)))

  override def edit(group: Group.Id, cfp: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != cfp) {
      find(group, data.slug).flatMap {
        case None => run(update(group, cfp)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have a cfp with slug ${data.slug}"))
      }
    } else {
      run(update(group, cfp)(data, by, now))
    }
  }

  override def find(id: Cfp.Id): IO[Option[Cfp]] = run(selectOne(id).option)

  override def find(slug: Cfp.Slug): IO[Option[Cfp]] = run(selectOne(slug).option)

  override def find(group: Group.Id, slug: Cfp.Slug): IO[Option[Cfp]] = run(selectOne(slug).option)

  override def find(id: Event.Id): IO[Option[Cfp]] = run(selectOne(id).option)

  override def findOpen(slug: Cfp.Slug, now: Instant): IO[Option[Cfp]] = run(selectOne(slug, now).option)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(ids: Seq[Cfp.Id]): IO[Seq[Cfp]] = runIn(selectAll)(ids)

  override def availableFor(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(selectPage(talk, _), params))

  override def list(group: Group.Id): IO[Seq[Cfp]] = run(selectAll(group).to[List])

  override def listOpen(now: Instant, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(selectPage(now, _), params))

  override def listAllOpen(group: Group.Id, now: Instant): IO[Seq[Cfp]] = run(selectAll(group, now).to[List])

  override def listTags(): IO[Seq[Tag]] = run(selectTags().to[List]).map(_.flatten.distinct)
}

object CfpRepoSql {
  private val _ = cfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "cfps"
  private[sql] val  fields = Seq("id", "group_id", "slug", "name", "start", "end", "description", "tags", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description", "tags")
  private val defaultSort = Page.OrderBy(Seq("-end", "name"))

  private def values(e: Cfp): Fragment =
    fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.start}, ${e.end}, ${e.description}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Cfp): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, name=${data.name}, start=${data.start}, end=${data.end}, description=${data.description}, tags=${data.tags}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, slug)).update
  }

  private[sql] def selectOne(id: Cfp.Id): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$id").query[Cfp]

  private[sql] def selectOne(slug: Cfp.Slug): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE slug=$slug").query[Cfp]

  private[sql] def selectOne(group: Group.Id, slug: Cfp.Slug): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, where(group, slug)).query[Cfp]

  private[sql] def selectOne(id: Event.Id): doobie.Query0[Cfp] = {
    val selectedTables = Fragment.const0(s"$table c INNER JOIN ${EventRepoSql.table} e ON e.cfp_id=c.id")
    val selectedFields = Fragment.const0(fields.map("c." + _).mkString(", "))
    buildSelect(selectedTables, selectedFields, fr0"WHERE e.id=$id").query[Cfp]
  }

  private[sql] def selectOne(slug: Cfp.Slug, now: Instant): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, where(slug, now)).query[Cfp]

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val talkCfps = buildSelect(ProposalRepoSql.tableFr, fr0"cfp_id", fr0"WHERE talk_id=$talk")
    val where = fr0"WHERE id NOT IN (" ++ talkCfps ++ fr0")"
    val page = paginate(params, searchFields, defaultSort, Some(where))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(now: Instant, params: Page.Params): (doobie.Query0[Cfp], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(where(now)))
    (buildSelect(tableFr, fieldsFr, page.all).query[Cfp], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(group: Group.Id): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE group_id=$group").query[Cfp]

  private[sql] def selectAll(ids: NonEmptyList[Cfp.Id]): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Cfp]

  private[sql] def selectAll(group: Group.Id, now: Instant): doobie.Query0[Cfp] =
    buildSelect(tableFr, fieldsFr, where(group, now)).query[Cfp]

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]

  private def where(group: Group.Id, slug: Cfp.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$slug"

  private def where(now: Instant): Fragment =
    fr0"WHERE (start IS NULL OR start < $now) AND (end IS NULL OR end > $now)"

  private def where(slug: Cfp.Slug, now: Instant): Fragment =
    where(now) ++ fr0" AND slug=$slug"

  private def where(group: Group.Id, now: Instant): Fragment =
    where(now) ++ fr0" AND group_id=$group"
}
