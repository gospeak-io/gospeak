package fr.gospeak.web.pages.user.talks.cfps

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Talk
import fr.gospeak.core.services.storage.{SpeakerCfpRepo, SpeakerProposalRepo, SpeakerTalkRepo}
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.user.talks.TalkCtrl
import fr.gospeak.web.pages.user.talks.cfps.CfpCtrl._
import fr.gospeak.web.utils.{UICtrl, UserReq}
import gospeak.libs.scala.domain.Page
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf,
              cfpRepo: SpeakerCfpRepo,
              talkRepo: SpeakerTalkRepo,
              proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette, conf) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(talk))
      cfps <- OptionT.liftF(cfpRepo.availableFor(talkElt.id, params))
      b = listBreadcrumb(talkElt)
    } yield Ok(html.list(talkElt, cfps)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }
}

object CfpCtrl {
  def listBreadcrumb(talk: Talk)(implicit req: UserReq[AnyContent]): Breadcrumb =
    TalkCtrl.breadcrumb(talk).add("Proposing" -> routes.CfpCtrl.list(talk.slug))
}
