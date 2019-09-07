package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Cfp, Talk, User}
import fr.gospeak.libs.scalautils.domain._

trait TalkRepo extends OrgaTalkRepo with SpeakerTalkRepo with UserTalkRepo with AuthTalkRepo with PublicTalkRepo with SuggestTalkRepo

trait OrgaTalkRepo

trait SpeakerTalkRepo {
  def create(data: Talk.Data, by: User.Id, now: Instant): IO[Talk]

  def edit(user: User.Id, talk: Talk.Slug)(data: Talk.Data, now: Instant): IO[Done]

  def editStatus(user: User.Id, talk: Talk.Slug)(status: Talk.Status): IO[Done]

  def editSlides(user: User.Id, talk: Talk.Slug)(slides: Slides, now: Instant): IO[Done]

  def editVideo(user: User.Id, talk: Talk.Slug)(video: Video, now: Instant): IO[Done]

  def removeSpeaker(user: User.Id, talk: Talk.Slug)(speaker: User.Id, now: Instant): IO[Done]

  def list(user: User.Id, params: Page.Params): IO[Page[Talk]]

  def listActive(user: User.Id, cfp: Cfp.Id, params: Page.Params): IO[Page[Talk]]

  def find(user: User.Id, talk: Talk.Slug): IO[Option[Talk]]

  def exists(talk: Talk.Slug): IO[Boolean]
}

trait UserTalkRepo {
  def addSpeaker(user: User.Id, talk: Talk.Id)(speaker: User.Id, now: Instant): IO[Done]

  def find(talk: Talk.Id): IO[Option[Talk]]

  def list(user: User.Id, params: Page.Params): IO[Page[Talk]]
}

trait AuthTalkRepo

trait PublicTalkRepo {
  def list(user: User.Id, status: Talk.Status, params: Page.Params): IO[Page[Talk]]
}

trait SuggestTalkRepo {
  def listTags(): IO[Seq[Tag]]
}
