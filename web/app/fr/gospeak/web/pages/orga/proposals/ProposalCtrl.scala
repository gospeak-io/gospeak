package fr.gospeak.web.pages.orga.proposals

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain._
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.proposals.ProposalCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class ProposalCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   userRepo: UserRepo,
                   groupRepo: GroupRepo,
                   cfpRepo: CfpRepo,
                   eventRepo: EventRepo,
                   proposalRepo: ProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      proposals <- OptionT.liftF(proposalRepo.list(groupElt.id, params))
      cfps <- OptionT.liftF(cfpRepo.list(proposals.items.map(_.cfp)))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.speakers.toList)))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_.event)))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, groupElt)
    } yield Ok(html.list(groupElt, proposals, cfps, speakers, events)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object ProposalCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.pages.orga.routes.GroupCtrl.detail(group)))
      .activeFor(routes.ProposalCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Proposals" -> routes.ProposalCtrl.list(group.slug))
}
