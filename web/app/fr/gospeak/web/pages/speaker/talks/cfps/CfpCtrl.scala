package fr.gospeak.web.pages.speaker.talks.cfps

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.storage.{SpeakerCfpRepo, SpeakerProposalRepo, SpeakerTalkRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.pages.speaker.talks.cfps.CfpCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              env: ApplicationConf.Env,
              cfpRepo: SpeakerCfpRepo,
              talkRepo: SpeakerTalkRepo,
              proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette, env) {
  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(req.user.id, talk))
      cfps <- OptionT.liftF(cfpRepo.availableFor(talkElt.id, params))
      b = listBreadcrumb(req.user, talkElt)
    } yield Ok(html.list(talkElt, cfps)(b))).value.map(_.getOrElse(talkNotFound(talk)))
  }
}

object CfpCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposing" -> routes.CfpCtrl.list(talk.slug))
}
