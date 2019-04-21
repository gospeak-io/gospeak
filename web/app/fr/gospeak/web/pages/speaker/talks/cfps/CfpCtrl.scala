package fr.gospeak.web.pages.speaker.talks.cfps

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Cfp, Talk, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.pages.speaker.talks.cfps.CfpCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              cfpRepo: SpeakerCfpRepo,
              talkRepo: SpeakerTalkRepo,
              proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(talk: Talk.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      talkElt <- OptionT(talkRepo.find(user, talk))
      cfps <- OptionT.liftF(cfpRepo.availableFor(talkElt.id, params))
      b = listBreadcrumb(req.identity.user, talkElt)
    } yield Ok(html.list(talkElt, cfps)(b))).value.map(_.getOrElse(talkNotFound(talk))).unsafeToFuture()
  }
}

object CfpCtrl {
  def listBreadcrumb(user: User, talk: Talk): Breadcrumb =
    TalkCtrl.breadcrumb(user, talk).add("Proposing" -> routes.CfpCtrl.list(talk.slug))
}
