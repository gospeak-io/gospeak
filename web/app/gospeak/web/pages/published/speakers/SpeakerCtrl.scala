package gospeak.web.pages.published.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Talk, User}
import gospeak.core.services.storage.{PublicGroupRepo, PublicProposalRepo, PublicTalkRepo, PublicUserRepo}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.speakers.SpeakerCtrl._
import gospeak.web.utils.UICtrl
import gospeak.libs.scala.domain.Page
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: PublicUserRepo,
                  talkRepo: PublicTalkRepo,
                  proposalRepo: PublicProposalRepo,
                  groupRepo: PublicGroupRepo) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    userRepo.listPublic(params).map(speakers => Ok(html.list(speakers)(listBreadcrumb())))
  }

  def detail(user: User.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      speakerElt <- OptionT(userRepo.findPublic(user))
      publicTalks <- OptionT.liftF(talkRepo.list(speakerElt.id, Talk.Status.Public, params))
      // acceptedProposals <- OptionT.liftF(proposalRepo.listPublicFull(speakerElt.id, params))
      groups <- OptionT.liftF(groupRepo.listFull(speakerElt.id))
      orgas <- OptionT.liftF(userRepo.list(groups.flatMap(_.owners.toList)))
      res = Ok(html.detail(speakerElt, publicTalks, groups, orgas)(breadcrumb(speakerElt)))
    } yield res).value.map(_.getOrElse(publicUserNotFound(user)))
  }
}

object SpeakerCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Speakers" -> routes.SpeakerCtrl.list())

  def breadcrumb(speaker: User): Breadcrumb =
    listBreadcrumb().add(speaker.name.value -> routes.SpeakerCtrl.detail(speaker.slug))
}
