package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.TalkRepo
import fr.gospeak.infra.services.storage.sql.tables.TalkTable
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain._

class TalkRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with TalkRepo {
  override def create(user: User.Id, data: Talk.Data, now: Instant): IO[Talk] =
    find(user, data.slug).flatMap {
      case None => run(TalkTable.insert, Talk(Talk.Id.generate(), data.slug, data.title, data.duration, Talk.Status.Draft, data.description, NonEmptyList.one(user), data.slides, data.video, Info(user, now)))
      case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
    }

  override def update(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done] = {
    if (data.slug != slug) {
      // FIXME: should also check for other speakers !!!
      find(user, data.slug).flatMap {
        case None => run(TalkTable.update(user, slug)(data, now))
        case _ => IO.raiseError(CustomException(s"You already have a talk with slug ${data.slug}"))
      }
    } else {
      run(TalkTable.update(user, slug)(data, now))
    }
  }

  override def updateStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done] = run(TalkTable.updateStatus(user, slug)(status))

  override def updateSlides(user: User.Id, slug: Talk.Slug)(slides: Slides, now: Instant): IO[Done] = run(TalkTable.updateSlides(user, slug)(slides, now))

  override def updateVideo(user: User.Id, slug: Talk.Slug)(video: Video, now: Instant): IO[Done] = run(TalkTable.updateVideo(user, slug)(video, now))

  override def find(user: User.Id, slug: Talk.Slug): IO[Option[Talk]] = run(TalkTable.selectOne(user, slug).option)

  override def list(user: User.Id, params: Page.Params): IO[Page[Talk]] = run(Queries.selectPage(TalkTable.selectPage(user, _), params))

  override def list(ids: Seq[Talk.Id]): IO[Seq[Talk]] = runIn(TalkTable.selectAll)(ids)
}
