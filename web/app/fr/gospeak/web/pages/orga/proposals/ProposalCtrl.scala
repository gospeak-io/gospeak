package fr.gospeak.web.pages.orga.proposals

import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.proposals.ProposalCtrl._
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   userRepo: OrgaUserRepo,
                   val groupRepo: OrgaGroupRepo,
                   cfpRepo: OrgaCfpRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val customParams = params.defaultOrderBy(proposalRepo.fields.title)
    for {
      proposals <- proposalRepo.listFull(customParams)
      speakers <- userRepo.list(proposals.items.flatMap(_.users))
      userRatings <- proposalRepo.listRatings(proposals.items.map(_.id))
    } yield Ok(html.list(req.group, proposals, speakers, userRatings)(listBreadcrumb))
  })
}

object ProposalCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb(req.group).add("Proposals" -> routes.ProposalCtrl.list(req.group.slug))
}
