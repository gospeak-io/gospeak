package fr.gospeak.web.pages.orga.proposals

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.proposals.ProposalCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   userRepo: OrgaUserRepo,
                   groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette, env) {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    val customParams = params.defaultOrderBy(proposalRepo.fields.title)
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      proposals <- OptionT.liftF(proposalRepo.listFull(groupElt.id, customParams))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users)))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, proposals, speakers)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object ProposalCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Proposals" -> routes.ProposalCtrl.list(group.slug))
}
