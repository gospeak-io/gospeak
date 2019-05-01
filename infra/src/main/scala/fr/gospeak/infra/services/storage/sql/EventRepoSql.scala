package fr.gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.{NonEmptyList, ValidatedNec}
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain._
import fr.gospeak.core.dto.EventFull
import fr.gospeak.core.services.EventRepo
import fr.gospeak.infra.services.storage.sql.EventRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}

class EventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with EventRepo {
  override def create(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event] =
    run(insert, Event(group, data, Info(by, now)))

  override def edit(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != event) {
      find(group, data.slug).flatMap {
        case None => run(update(group, event)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      run(update(group, event)(data, by, now))
    }
  }

  override def attachCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): IO[Done] =
    run(updateCfp(group, event)(cfp, by, now))

  override def editTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done] =
    run(updateTalks(group, event)(talks, by, now))

  override def list(group: Group.Id, params: Page.Params): IO[Page[Event]] = run(Queries.selectPage(selectPage(group, _), params))

  override def list(ids: Seq[Event.Id]): IO[Seq[Event]] = runIn[Event.Id, Event](selectAll)(ids)

  override def listAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]] =
    run(Queries.selectPage(selectAllAfter(group, now.truncatedTo(ChronoUnit.DAYS), _), params))

  override def listTags(): IO[Seq[Tag]] = run(selectTags().to[List]).map(_.flatten.distinct)

  override def find(group: Group.Id, event: Event.Slug): IO[Option[Event]] = run(selectOne(group, event).option)

  override def findFull(user: User.Id, group: Group.Slug, event: Event.Slug): IO[Option[ValidatedNec[String, EventFull]]] =
    (for {
      res <- run(selectOne(user, group, event).option)
      talks <- res.flatMap(a => NonEmptyList.fromList(a._2.talks.toList)).map(ids => run(ProposalRepoSql.selectAll(ids).to[List])).getOrElse(IO.pure(Seq.empty[Proposal]))
      speakers <- NonEmptyList.fromList(talks.toList.flatMap(_.speakers.toList).distinct).map(ids => run(UserRepoSql.selectAll(ids).to[List])).getOrElse(IO.pure(Seq.empty[User]))
    } yield res.map(r => (r, talks, speakers))).map(_.map { case ((g, e, c, v), t, s) => EventFull.from(g, e, c, v, t, s) })
}

object EventRepoSql {
  private val _ = eventIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "events"
  private val fields = Seq("id", "group_id", "cfp_id", "slug", "name", "start", "description", "venue", "talks", "tags", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "name", "description", "tags")
  private val defaultSort = Page.OrderBy("-start")

  private def values(e: Event): Fragment =
    fr0"${e.id}, ${e.group}, ${e.cfp}, ${e.slug}, ${e.name}, ${e.start}, ${e.description}, ${e.venue}, ${e.talks}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Event): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"cfp_id=${data.cfp}, slug=${data.slug}, name=${data.name}, start=${data.start}, venue=${data.venue}, tags=${data.tags}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, event)).update
  }

  private[sql] def updateCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"cfp_id=$cfp, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, event)).update
  }

  private[sql] def updateTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"talks=$talks, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, event)).update
  }

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Event], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Event], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectAll(ids: NonEmptyList[Event.Id]): doobie.Query0[Event] =
    buildSelect(tableFr, fieldsFr, fr"WHERE" ++ Fragments.in(fr"id", ids)).query[Event]

  private[sql] def selectAllAfter(group: Group.Id, now: Instant, params: Page.Params): (doobie.Query0[Event], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group AND start > $now"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Event], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]

  private[sql] def selectOne(group: Group.Id, event: Event.Slug): doobie.Query0[Event] =
    buildSelect(tableFr, fieldsFr, where(group, event)).query[Event]

  private[sql] def selectOne(user: User.Id, group: Group.Slug, event: Event.Slug): doobie.Query0[(Group, Event, Option[Cfp], Option[(Venue, Partner)])] = {
    val selectedTables = Fragment.const0(s"${GroupRepoSql.table} g " +
      s"INNER JOIN $table e ON e.group_id=g.id " +
      s"LEFT JOIN ${CfpRepoSql.table} c ON c.id=e.cfp_id " +
      s"LEFT JOIN ${VenueRepoSql.table} v ON v.id=e.venue " +
      s"LEFT JOIN ${PartnerRepoSql.table} p ON p.id=v.partner_id ")
    val selectedFields = Fragment.const0((GroupRepoSql.fields.map("g." + _) ++
      fields.map("e." + _) ++
      CfpRepoSql.fields.map("c." + _) ++
      VenueRepoSql.fields.map("v." + _) ++
      PartnerRepoSql.fields.map("p." + _)).mkString(", "))
    val where = fr0"WHERE g.owners LIKE ${"%" + user.value + "%"} AND g.slug=$group AND e.slug=$event"
    buildSelect(selectedTables, selectedFields, where).query[(Group, Event, Option[Cfp], Option[(Venue, Partner)])]
  }

  private def where(group: Group.Id, event: Event.Slug): Fragment =
    fr0"WHERE group_id=$group AND slug=$event"
}
