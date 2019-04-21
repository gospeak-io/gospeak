package fr.gospeak.web.pages.orga.proposals

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain._
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.proposals.ProposalCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: OrgaUserRepo,
                   groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      proposals <- OptionT.liftF(proposalRepo.list(groupElt.id, params))
      cfps <- OptionT.liftF(cfpRepo.list(proposals.items.map(_.cfp)))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users)))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_.event)))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, proposals, cfps, speakers, events)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Proposals" -> routes.ProposalCtrl.list(group.slug))
}
