package fr.gospeak.web.pages.user.proposals

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.services.storage.SpeakerProposalRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.user.proposals.ProposalCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.{UICtrl, UserReq}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    proposalRepo.listFull(params).map(proposals => Ok(html.list(proposals)(listBreadcrumb)))
  }
}

object ProposalCtrl {
  def listBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Proposals" -> routes.ProposalCtrl.list())
}
