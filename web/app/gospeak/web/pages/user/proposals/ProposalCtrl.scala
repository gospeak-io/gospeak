package gospeak.web.pages.user.proposals

import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.services.storage.SpeakerProposalRepo
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.user.proposals.ProposalCtrl._
import gospeak.web.pages.user.UserCtrl
import gospeak.web.utils.{UICtrl, UserReq}
import gospeak.libs.scala.domain.Page
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
