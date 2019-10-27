package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Cfp, Talk, User}
import fr.gospeak.libs.scalautils.domain._

trait TalkRepo extends OrgaTalkRepo with SpeakerTalkRepo with UserTalkRepo with AuthTalkRepo with PublicTalkRepo with SuggestTalkRepo

trait OrgaTalkRepo

trait SpeakerTalkRepo {
  def create(data: Talk.Data, by: User.Id, now: Instant): IO[Talk]

  def edit(talk: Talk.Slug)(data: Talk.Data, by: User.Id, now: Instant): IO[Done]

  def editStatus(talk: Talk.Slug)(status: Talk.Status, by: User.Id): IO[Done]

  def editSlides(talk: Talk.Slug)(slides: Slides, by: User.Id, now: Instant): IO[Done]

  def editVideo(talk: Talk.Slug)(video: Video, by: User.Id, now: Instant): IO[Done]

  def removeSpeaker(talk: Talk.Slug)(speaker: User.Id, by: User.Id, now: Instant): IO[Done]

  def find(talk: Talk.Id): IO[Option[Talk]]

  def list(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def listActive(user: User.Id, cfp: Cfp.Id, params: Page.Params): IO[Page[Talk]]

  def find(user: User.Id, talk: Talk.Slug): IO[Option[Talk]]

  def exists(talk: Talk.Slug): IO[Boolean]
}

trait UserTalkRepo {
  def addSpeaker(talk: Talk.Id)(speaker: User.Id, by: User.Id, now: Instant): IO[Done]

  def list(user: User.Id, params: Page.Params): IO[Page[Talk]]
}

trait AuthTalkRepo

trait PublicTalkRepo {
  def list(user: User.Id, status: Talk.Status, params: Page.Params): IO[Page[Talk]]
}

trait SuggestTalkRepo {
  def listTags(): IO[Seq[Tag]]
}
