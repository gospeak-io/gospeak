package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.{BasicCtx, UserCtx}
import gospeak.core.domain.{Cfp, Talk, User}
import gospeak.core.services.storage.TalkRepo
import gospeak.infra.services.storage.sql.TalkRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{PROPOSALS, TALKS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._
import gospeak.libs.sql.doobie.{DbCtx, Field, Query}
import gospeak.libs.sql.dsl.Cond

class TalkRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with TalkRepo {
  override def create(data: Talk.Data)(implicit ctx: UserCtx): IO[Talk] =
    find(data.slug).flatMap {
      case None => insert(Talk(data, Talk.Status.Public, NonEmptyList.one(ctx.user.id), ctx.info)).run(xa)
      case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
    }

  override def edit(talk: Talk.Slug, data: Talk.Data)(implicit ctx: UserCtx): IO[Done] = {
    if (data.slug != talk) {
      find(data.slug).flatMap {
        case None => update(talk)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
      }
    } else {
      update(talk)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def editStatus(talk: Talk.Slug, status: Talk.Status)(implicit ctx: UserCtx): IO[Done] = updateStatus(talk)(status, ctx.user.id).run(xa)

  override def editSlides(talk: Talk.Slug, slides: Url.Slides)(implicit ctx: UserCtx): IO[Done] = updateSlides(talk)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(talk: Talk.Slug, video: Url.Video)(implicit ctx: UserCtx): IO[Done] = updateVideo(talk)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(talk: Talk.Id, by: User.Id)(implicit ctx: UserCtx): IO[Done] =
    find(talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.speakers.toList.contains(ctx.user.id)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(talkElt.slug)(talkElt.speakers.append(ctx.user.id), by, ctx.now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Done] =
    find(talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("talk creator can't be removed"))
        } else if (talkElt.speakers.toList.contains(speaker)) {
          talkElt.speakers.filter(_ != speaker).toNel.map { speakers =>
            updateSpeakers(talk)(speakers, ctx.user.id, ctx.now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def find(talk: Talk.Id): IO[Option[Talk]] = selectOne(talk).runOption(xa)

  override def find(talk: Talk.Slug)(implicit ctx: UserCtx): IO[Option[Talk]] = selectOne(ctx.user.id, talk).runOption(xa)

  override def findPublic(talk: Talk.Slug, speaker: User.Id): IO[Option[Talk]] = selectOne(speaker, talk, Talk.Status.Public).runOption(xa)

  override def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(params).run(xa)

  override def listAll(user: User.Id, status: Talk.Status): IO[List[Talk]] = selectAll(user, status).runList(xa)

  override def listAllPublicSlugs(): IO[List[(Talk.Slug, NonEmptyList[User.Id])]] = selectAllPublicSlugs().runList(xa)

  override def listCurrent(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(Talk.Status.current, params).run(xa)

  override def listCurrent(cfp: Cfp.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(cfp, Talk.Status.current, params).run(xa)

  override def exists(talk: Talk.Slug): IO[Boolean] = selectOne(talk).runExists(xa)

  override def listTags(): IO[List[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object TalkRepoSql {
  private val _ = talkIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.talks

  private[sql] def insert(e: Talk): Query.Insert[Talk] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[Talk](e, _ => values)
    val q2 = TALKS.insert.values(e.id, e.slug, e.status, e.title, e.duration, e.description, e.message, e.speakers, e.slides, e.video, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(talk: Talk.Slug)(d: Talk.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"slug=${d.slug}, title=${d.title}, duration=${d.duration}, description=${d.description}, message=${d.message}, slides=${d.slides}, video=${d.video}, tags=${d.tags}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(where(by, talk))
    val q2 = TALKS.update.set(_.SLUG, d.slug).set(_.TITLE, d.title).set(_.DURATION, d.duration).set(_.DESCRIPTION, d.description).set(_.MESSAGE, d.message).set(_.SLIDES, d.slides).set(_.VIDEO, d.video).set(_.TAGS, d.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(by, talk))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateStatus(talk: Talk.Slug)(status: Talk.Status, by: User.Id): Query.Update = {
    val q1 = table.update(fr0"status=$status").where(where(by, talk))
    val q2 = TALKS.update.set(_.STATUS, status).where(where2(by, talk))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateSlides(talk: Talk.Slug)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by").where(where(by, talk))
    val q2 = TALKS.update.setOpt(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(by, talk))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateVideo(talk: Talk.Slug)(video: Url.Video, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"video=$video, updated_at=$now, updated_by=$by").where(where(by, talk))
    val q2 = TALKS.update.setOpt(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(by, talk))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateSpeakers(talk: Talk.Slug)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by").where(where(by, talk))
    val q2 = TALKS.update.set(_.SPEAKERS, speakers).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(by, talk))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(talk: Talk.Id): Query.Select[Talk] = {
    val q1 = table.select[Talk].where(fr0"t.id=$talk")
    val q2 = TALKS.select.where(_.ID is talk).option[Talk]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(talk: Talk.Slug): Query.Select[Talk] = {
    val q1 = table.select[Talk].where(fr0"t.slug=$talk")
    val q2 = TALKS.select.where(_.SLUG is talk).exists[Talk]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug): Query.Select[Talk] = {
    val q1 = table.select[Talk].where(where(user, talk))
    val q2 = TALKS.select.where(where2(user, talk)).option[Talk]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug, status: Talk.Status): Query.Select[Talk] = {
    val q1 = table.select[Talk].where(fr0"t.speakers LIKE ${"%" + user.value + "%"} AND t.slug=$talk AND t.status=$status").one
    val q2 = TALKS.select.where(t => t.SPEAKERS.like("%" + user.value + "%") and t.SLUG.is(talk) and t.STATUS.is(status)).option[Talk](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Talk] = {
    val q1 = table.selectPage[Talk](params, adapt(ctx)).where(fr0"t.speakers LIKE ${"%" + ctx.user.id.value + "%"}")
    val q2 = TALKS.select.where(_.SPEAKERS.like("%" + ctx.user.id.value + "%")).page[Talk](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(user: User.Id, status: Talk.Status): Query.Select[Talk] = {
    val q1 = table.select[Talk].where(fr0"t.speakers LIKE ${"%" + user.value + "%"} AND t.status=$status")
    val q2 = TALKS.select.where(t => t.SPEAKERS.like("%" + user.value + "%") and t.STATUS.is(status)).all[Talk]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllPublicSlugs(): Query.Select[(Talk.Slug, NonEmptyList[User.Id])] = {
    val q1 = table.select[(Talk.Slug, NonEmptyList[User.Id])].fields(Field("slug", "t"), Field("speakers", "t")).where(fr0"t.status=${Talk.Status.Public: Talk.Status}")
    val q2 = TALKS.select.withFields(_.SLUG, _.SPEAKERS).where(_.STATUS is Talk.Status.Public).all[(Talk.Slug, NonEmptyList[User.Id])]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(status: NonEmptyList[Talk.Status], params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Talk] = {
    val q1 = table.selectPage[Talk](params, adapt(ctx)).where(fr0"t.speakers LIKE ${"%" + ctx.user.id.value + "%"} AND " ++ Fragments.in(fr"t.status", status))
    val q2 = TALKS.select.where(t => t.SPEAKERS.like("%" + ctx.user.id.value + "%") and t.STATUS.in(status)).page[Talk](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(cfp: Cfp.Id, status: NonEmptyList[Talk.Status], params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[Talk] = {
    val cfpTalks = Tables.proposals.select[Talk.Id].fields(Field("talk_id", "p")).where(fr0"p.cfp_id=$cfp").fr
    val q1 = table.selectPage[Talk](params, adapt(ctx)).where(fr0"t.speakers LIKE ${"%" + ctx.user.id.value + "%"} AND t.id NOT IN (" ++ cfpTalks ++ fr0") AND " ++ Fragments.in(fr"t.status", status))
    val CFP_TALKS = PROPOSALS.select.withFields(_.TALK_ID).where(_.CFP_ID.is(cfp)).all[Talk.Id]
    val q2 = TALKS.select.where(t => t.SPEAKERS.like("%" + ctx.user.id.value + "%") and t.ID.notIn(CFP_TALKS) and t.STATUS.in(status)).page[Talk](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectTags(): Query.Select[List[Tag]] = {
    val q1 = table.select[List[Tag]].fields(Field("tags", "t"))
    val q2 = TALKS.select.withFields(_.TAGS).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(user: User.Id, talk: Talk.Slug): Fragment = fr0"t.speakers LIKE ${"%" + user.value + "%"} AND t.slug=$talk"

  private def where2(user: User.Id, talk: Talk.Slug): Cond = TALKS.SPEAKERS.like("%" + user.value + "%") and TALKS.SLUG.is(talk)

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
