package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page, Slides, Video}

trait TalkRepo extends OrgaTalkRepo with SpeakerTalkRepo with UserTalkRepo with AuthTalkRepo

trait OrgaTalkRepo

trait SpeakerTalkRepo {
  def create(user: User.Id, data: Talk.Data, now: Instant): IO[Talk]

  def edit(user: User.Id, slug: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done]

  def editStatus(user: User.Id, slug: Talk.Slug)(status: Talk.Status): IO[Done]

  def editSlides(user: User.Id, slug: Talk.Slug)(slides: Slides, now: Instant): IO[Done]

  def editVideo(user: User.Id, slug: Talk.Slug)(video: Video, now: Instant): IO[Done]

  def list(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def find(user: User.Id, slug: Talk.Slug): IO[Option[Talk]]
}

trait UserTalkRepo {
  def list(user: User.Id, params: Page.Params): IO[Page[Talk]]
}

trait AuthTalkRepo
