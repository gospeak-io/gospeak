package fr.gospeak.web.user.groups.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Group, Proposal, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.cfps.CfpCtrl
import fr.gospeak.web.user.groups.speakers.SpeakerCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import fr.gospeak.web.user.groups.routes.{GroupCtrl => GroupRoutes}

class SpeakerCtrl(cc: ControllerComponents,
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
      speakers <- OptionT.liftF(userRepo.list(groupElt.id, params))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, groupElt)
    } yield Ok(html.list(groupElt, speakers)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def detail(group: Group.Slug, speaker: User.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      speakerElt <- OptionT(userRepo.find(speaker))
      h = header(group)
      b = breadcrumb(req.identity.user.name, groupElt, speakerElt)
    } yield Ok(html.detail(groupElt, speakerElt)(h, b))).value.map(_.getOrElse(speakerNotFound(group, speaker))).unsafeToFuture()
  }

}

object SpeakerCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", GroupRoutes.detail(group)))
      .activeFor(routes.SpeakerCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("CFPs" -> routes.SpeakerCtrl.list(group.slug))

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: Group, speaker: User): Breadcrumb =
    listBreadcrumb(user, group).add(speaker.name.value -> routes.SpeakerCtrl.detail(group.slug, speaker.slug))
}

