package fr.gospeak.web.pages.speaker.proposals

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.services.storage.SpeakerProposalRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.proposals.ProposalCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.{UICtrl, UserReq}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette, env) with UICtrl.UserAction {
  def list(params: Page.Params): Action[AnyContent] = UserAction(implicit req => implicit ctx => {
    proposalRepo.listFull(params).map(proposals => Ok(html.list(proposals)(listBreadcrumb)))
  })
}

object ProposalCtrl {
  def listBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Proposals" -> routes.ProposalCtrl.list())
}
