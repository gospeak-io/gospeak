package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.UserCtx
import gospeak.core.domain.{Cfp, Talk, User}
import gospeak.libs.scala.domain._

trait TalkRepo extends OrgaTalkRepo with SpeakerTalkRepo with UserTalkRepo with AuthTalkRepo with PublicTalkRepo with SuggestTalkRepo

trait OrgaTalkRepo

trait SpeakerTalkRepo {
  def create(data: Talk.Data)(implicit ctx: UserCtx): IO[Talk]

  def edit(talk: Talk.Slug, data: Talk.Data)(implicit ctx: UserCtx): IO[Done]

  def editStatus(talk: Talk.Slug, status: Talk.Status)(implicit ctx: UserCtx): IO[Done]

  def editSlides(talk: Talk.Slug, slides: Slides)(implicit ctx: UserCtx): IO[Done]

  def editVideo(talk: Talk.Slug, video: Video)(implicit ctx: UserCtx): IO[Done]

  def removeSpeaker(talk: Talk.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Done]

  def find(talk: Talk.Id): IO[Option[Talk]]

  def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]]

  def listCurrent(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]]

  def listCurrent(cfp: Cfp.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]]

  def find(talk: Talk.Slug)(implicit ctx: UserCtx): IO[Option[Talk]]

  def exists(talk: Talk.Slug): IO[Boolean]
}

trait UserTalkRepo {
  def addSpeaker(talk: Talk.Id, by: User.Id)(implicit ctx: UserCtx): IO[Done]
}

trait AuthTalkRepo

trait PublicTalkRepo {
  def findPublic(talk: Talk.Slug, speaker: User.Id): IO[Option[Talk]]

  def find(talk: Talk.Id): IO[Option[Talk]]

  def listAll(user: User.Id, status: Talk.Status): IO[Seq[Talk]]
}

trait SuggestTalkRepo {
  def listTags(): IO[Seq[Tag]]
}
