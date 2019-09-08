package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Cfp, Talk, User}
import fr.gospeak.core.services.storage.TalkRepo
import fr.gospeak.infra.services.storage.sql.TalkRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Page, Slides, Video, _}

class TalkRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with TalkRepo {
  override def create(data: Talk.Data, by: User.Id, now: Instant): IO[Talk] =
    find(data.slug).flatMap {
      case None => run(insert, Talk(data, Talk.Status.Draft, NonEmptyList.one(by), Info(by, now)))
      case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
    }

  override def edit(talk: Talk.Slug)(data: Talk.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != talk) {
      find(data.slug).flatMap {
        case None => run(update(talk)(data, by, now))
        case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
      }
    } else {
      run(update(talk)(data, by, now))
    }
  }

  override def editStatus(talk: Talk.Slug)(status: Talk.Status, by: User.Id): IO[Done] = run(updateStatus(talk)(status, by))

  override def editSlides(talk: Talk.Slug)(slides: Slides, by: User.Id, now: Instant): IO[Done] = run(updateSlides(talk)(slides, by, now))

  override def editVideo(talk: Talk.Slug)(video: Video, by: User.Id, now: Instant): IO[Done] = run(updateVideo(talk)(video, by, now))

  override def addSpeaker(talk: Talk.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.speakers.toList.contains(speaker)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          run(updateSpeakers(talkElt.slug)(talkElt.speakers.append(speaker), by, now))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug)(speaker: User.Id, by: User.Id, now: Instant): IO[Done] =
    find(by, talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("talk creator can't be removed"))
        } else if (talkElt.speakers.toList.contains(speaker)) {
          NonEmptyList.fromList(talkElt.speakers.filter(_ != speaker)).map { speakers =>
            run(updateSpeakers(talk)(speakers, by, now))
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def find(talk: Talk.Id): IO[Option[Talk]] = run(selectOne(talk).option)

  override def list(user: User.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(selectPage(user, _), params))

  override def list(user: User.Id, status: Talk.Status, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(selectPage(user, status, _), params))

  override def listActive(user: User.Id, cfp: Cfp.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(selectPage(user, cfp, Talk.Status.active, _), params))

  private def find(talk: Talk.Slug): IO[Option[Talk]] = run(selectOne(talk).option)

  override def find(user: User.Id, talk: Talk.Slug): IO[Option[Talk]] = run(selectOne(user, talk).option)

  override def exists(talk: Talk.Slug): IO[Boolean] = run(selectOne(talk).option.map(_.isDefined))

  override def listTags(): IO[Seq[Tag]] = run(selectTags().to[List]).map(_.flatten.distinct)
}

object TalkRepoSql {
  private val _ = talkIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table = "talks"
  private[sql] val fields = Seq("id", "slug", "status", "title", "duration", "description", "speakers", "slides", "video", "tags", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields = Seq("id", "slug", "title", "description", "tags")
  private val defaultSort = Page.OrderBy("title")

  private def values(e: Talk): Fragment =
    fr0"${e.id}, ${e.slug}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Talk): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(talk: Talk.Slug)(data: Talk.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"slug=${data.slug}, title=${data.title}, duration=${data.duration}, description=${data.description}, slides=${data.slides}, video=${data.video}, tags=${data.tags}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(by, talk)).update
  }

  private[sql] def updateStatus(talk: Talk.Slug)(status: Talk.Status, by: User.Id): doobie.Update0 =
    buildUpdate(tableFr, fr0"status=$status", where(by, talk)).update

  private[sql] def updateSlides(talk: Talk.Slug)(slides: Slides, by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"slides=$slides, updated=$now, updated_by=$by", where(by, talk)).update

  private[sql] def updateVideo(talk: Talk.Slug)(video: Video, by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"video=$video, updated=$now, updated_by=$by", where(by, talk)).update

  private[sql] def updateSpeakers(talk: Talk.Slug)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): doobie.Update0 =
    buildUpdate(tableFr, fr0"speakers=$speakers, updated=$now, updated_by=$by", where(by, talk)).update

  private[sql] def selectOne(talk: Talk.Id): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, fr0"WHERE id=$talk").query[Talk]

  private[sql] def selectOne(talk: Talk.Slug): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, where(talk)).query[Talk]

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug): doobie.Query0[Talk] =
    buildSelect(tableFr, fieldsFr, where(user, talk)).query[Talk]

  private[sql] def selectPage(user: User.Id, params: Page.Params): (doobie.Query0[Talk], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE speakers LIKE ${"%" + user.value + "%"}"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Talk], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(user: User.Id, status: Talk.Status, params: Page.Params): (doobie.Query0[Talk], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE speakers LIKE ${"%" + user.value + "%"} AND status=$status"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Talk], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectPage(user: User.Id, cfp: Cfp.Id, status: NonEmptyList[Talk.Status], params: Page.Params): (doobie.Query0[Talk], doobie.Query0[Long]) = {
    val cfpTalks = buildSelect(ProposalRepoSql.tableFr, fr0"talk_id", fr0"WHERE cfp_id=$cfp")
    val where = fr0"WHERE speakers LIKE ${"%" + user.value + "%"} AND id NOT IN (" ++ cfpTalks ++ fr0") AND " ++ Fragments.in(fr"status", status)
    val page = paginate(params, searchFields, defaultSort, Some(where))
    (buildSelect(tableFr, fieldsFr, page.all).query[Talk], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectTags(): doobie.Query0[Seq[Tag]] =
    Fragment.const0(s"SELECT tags FROM $table").query[Seq[Tag]]

  private def where(talk: Talk.Slug): Fragment =
    fr0"WHERE slug=$talk"

  private def where(user: User.Id, talk: Talk.Slug): Fragment =
    fr0"WHERE speakers LIKE ${"%" + user.value + "%"} AND slug=$talk"
}
