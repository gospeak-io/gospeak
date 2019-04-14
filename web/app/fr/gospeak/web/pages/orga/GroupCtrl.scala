package fr.gospeak.web.pages.orga

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                eventRepo: OrgaEventRepo,
                proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def detail(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req: SecuredRequest[CookieEnv, AnyContent] =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      events <- OptionT.liftF(eventRepo.listAfter(groupElt.id, now, Page.Params.defaults.orderBy("start")))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.speakers.toList)))
      b = breadcrumb(groupElt)
    } yield Ok(html.detail(groupElt, events, proposals, speakers)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def breadcrumb(group: Group): Breadcrumb =
    Breadcrumb(group.name.value -> routes.GroupCtrl.detail(group.slug))
}
