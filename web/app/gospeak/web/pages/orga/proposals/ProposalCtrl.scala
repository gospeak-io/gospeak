package gospeak.web.pages.orga.proposals

import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.services.storage._
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.orga.GroupCtrl
import gospeak.web.pages.orga.proposals.ProposalCtrl._
import gospeak.web.utils.{OrgaReq, UICtrl}
import gospeak.libs.scala.domain.Page
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   userRepo: OrgaUserRepo,
                   val groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    val customParams = params.defaultOrderBy(proposalRepo.fields.title)
    for {
      proposals <- proposalRepo.listFull(customParams)
      speakers <- userRepo.list(proposals.items.flatMap(_.users))
      userRatings <- proposalRepo.listRatings(proposals.items.map(_.id))
    } yield Ok(html.list(proposals, speakers, userRatings)(listBreadcrumb))
  }
}

object ProposalCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Proposals" -> routes.ProposalCtrl.list(req.group.slug))
}
