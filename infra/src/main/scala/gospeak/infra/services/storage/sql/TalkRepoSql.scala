package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.{Cond, Query}
import gospeak.core.domain.utils.UserCtx
import gospeak.core.domain.{Cfp, Talk, User}
import gospeak.core.services.storage.TalkRepo
import gospeak.infra.services.storage.sql.TalkRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{PROPOSALS, TALKS}
import gospeak.infra.services.storage.sql.database.tables.TALKS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

class TalkRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with TalkRepo {
  override def create(data: Talk.Data)(implicit ctx: UserCtx): IO[Talk] =
    find(data.slug).flatMap {
      case None =>
        val talk = Talk(data, Talk.Status.Public, NonEmptyList.one(ctx.user.id), ctx.info)
        insert(talk).run(xa).map(_ => talk)
      case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
    }

  override def edit(talk: Talk.Slug, data: Talk.Data)(implicit ctx: UserCtx): IO[Unit] = {
    if (data.slug != talk) {
      find(data.slug).flatMap {
        case None => update(talk)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
      }
    } else {
      update(talk)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def editStatus(talk: Talk.Slug, status: Talk.Status)(implicit ctx: UserCtx): IO[Unit] = updateStatus(talk)(status, ctx.user.id).run(xa)

  override def editSlides(talk: Talk.Slug, slides: Url.Slides)(implicit ctx: UserCtx): IO[Unit] = updateSlides(talk)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(talk: Talk.Slug, video: Url.Video)(implicit ctx: UserCtx): IO[Unit] = updateVideo(talk)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(talk: Talk.Id, by: User.Id)(implicit ctx: UserCtx): IO[Unit] =
    find(talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.speakers.toList.contains(ctx.user.id)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(talkElt.slug)(talkElt.speakers.append(ctx.user.id), by, ctx.now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Unit] =
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

  override def find(talk: Talk.Id): IO[Option[Talk]] = selectOne(talk).run(xa)

  override def find(talk: Talk.Slug)(implicit ctx: UserCtx): IO[Option[Talk]] = selectOne(ctx.user.id, talk).run(xa)

  override def findPublic(talk: Talk.Slug, speaker: User.Id): IO[Option[Talk]] = selectOne(speaker, talk, Talk.Status.Public).run(xa)

  override def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(params).run(xa).map(_.fromSql)

  override def listAll(user: User.Id, status: Talk.Status): IO[List[Talk]] = selectAll(user, status).run(xa)

  override def listAllPublicSlugs(): IO[List[(Talk.Slug, NonEmptyList[User.Id])]] = selectAllPublicSlugs().run(xa)

  override def listCurrent(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(Talk.Status.current, params).run(xa).map(_.fromSql)

  override def listCurrent(cfp: Cfp.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(cfp, Talk.Status.current, params).run(xa).map(_.fromSql)

  override def exists(talk: Talk.Slug): IO[Boolean] = selectOne(talk).run(xa)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)
}

object TalkRepoSql {
  private[sql] def insert(e: Talk): Query.Insert[TALKS] =
  // TALKS.insert.values(e.id, e.slug, e.status, e.title, e.duration, e.description, e.message, e.speakers, e.slides, e.video, e.tags, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    TALKS.insert.values(fr0"${e.id}, ${e.slug}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(talk: Talk.Slug)(d: Talk.Data, by: User.Id, now: Instant): Query.Update[TALKS] =
    TALKS.update.set(_.SLUG, d.slug).set(_.TITLE, d.title).set(_.DURATION, d.duration).set(_.DESCRIPTION, d.description).set(_.MESSAGE, d.message).set(_.SLIDES, d.slides).set(_.VIDEO, d.video).set(_.TAGS, d.tags).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(by, talk))

  private[sql] def updateStatus(talk: Talk.Slug)(status: Talk.Status, by: User.Id): Query.Update[TALKS] =
    TALKS.update.set(_.STATUS, status).where(where(by, talk))

  private[sql] def updateSlides(talk: Talk.Slug)(slides: Url.Slides, by: User.Id, now: Instant): Query.Update[TALKS] =
    TALKS.update.set(_.SLIDES, slides).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(by, talk))

  private[sql] def updateVideo(talk: Talk.Slug)(video: Url.Video, by: User.Id, now: Instant): Query.Update[TALKS] =
    TALKS.update.set(_.VIDEO, video).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(by, talk))

  private[sql] def updateSpeakers(talk: Talk.Slug)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Query.Update[TALKS] =
    TALKS.update.set(_.SPEAKERS, speakers).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(by, talk))

  private[sql] def selectOne(talk: Talk.Id): Query.Select.Optional[Talk] =
    TALKS.select.where(_.ID is talk).option[Talk]

  private[sql] def selectOne(talk: Talk.Slug): Query.Select.Exists[Talk] =
    TALKS.select.where(_.SLUG is talk).exists[Talk]

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug): Query.Select.Optional[Talk] =
    TALKS.select.where(where(user, talk)).option[Talk]

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug, status: Talk.Status): Query.Select.Optional[Talk] =
    TALKS.select.where(t => t.SPEAKERS.like("%" + user.value + "%") and t.SLUG.is(talk) and t.STATUS.is(status)).option[Talk](limit = true)

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Talk] =
    TALKS.select.where(_.SPEAKERS.like("%" + ctx.user.id.value + "%")).page[Talk](params.toSql, ctx.toSql)

  private[sql] def selectAll(user: User.Id, status: Talk.Status): Query.Select.All[Talk] =
    TALKS.select.where(t => t.SPEAKERS.like("%" + user.value + "%") and t.STATUS.is(status)).all[Talk]

  private[sql] def selectAllPublicSlugs(): Query.Select.All[(Talk.Slug, NonEmptyList[User.Id])] =
    TALKS.select.withFields(_.SLUG, _.SPEAKERS).where(_.STATUS is Talk.Status.Public).all[(Talk.Slug, NonEmptyList[User.Id])]

  private[sql] def selectPage(status: NonEmptyList[Talk.Status], params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Talk] =
    TALKS.select.where(t => t.SPEAKERS.like("%" + ctx.user.id.value + "%") and t.STATUS.in(status)).page[Talk](params.toSql, ctx.toSql)

  private[sql] def selectPage(cfp: Cfp.Id, status: NonEmptyList[Talk.Status], params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[Talk] = {
    val CFP_TALKS = PROPOSALS.select.withFields(_.TALK_ID).where(_.CFP_ID.is(cfp)).all[Talk.Id]
    TALKS.select.where(t => t.SPEAKERS.like("%" + ctx.user.id.value + "%") and t.ID.notIn(CFP_TALKS) and t.STATUS.in(status)).page[Talk](params.toSql, ctx.toSql)
  }

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
     TALKS.select.withFields(_.TAGS).all[List[Tag]]

  private def where(user: User.Id, talk: Talk.Slug): Cond = TALKS.SPEAKERS.like("%" + user.value + "%") and TALKS.SLUG.is(talk)
}
