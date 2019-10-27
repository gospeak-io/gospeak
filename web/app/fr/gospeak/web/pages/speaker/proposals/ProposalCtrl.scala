package fr.gospeak.web.pages.speaker.proposals

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.storage.SpeakerProposalRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.speaker.proposals.ProposalCtrl._
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   proposalRepo: SpeakerProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      proposals <- proposalRepo.listFull(user, params)
      b = listBreadcrumb(req.identity.user)
    } yield Ok(html.list(proposals)(b))).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Proposals" -> routes.ProposalCtrl.list())
}
