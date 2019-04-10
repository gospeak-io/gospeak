package fr.gospeak.web.pages.orga.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.pages.orga.speakers.SpeakerCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: OrgaUserRepo,
                  groupRepo: OrgaGroupRepo,
                  eventRepo: OrgaEventRepo,
                  proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      speakers <- OptionT.liftF(userRepo.speakers(groupElt.id, params))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, groupElt)
    } yield Ok(html.list(groupElt, speakers)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def detail(group: Group.Slug, speaker: User.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      speakerElt <- OptionT(userRepo.find(speaker))
      proposals <- OptionT.liftF(proposalRepo.list(groupElt.id, speakerElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_._2.speakers.toList)))
      events <- OptionT.liftF(eventRepo.list(proposals.items.flatMap(_._2.event)))
      h = header(group)
      b = breadcrumb(req.identity.user.name, groupElt, speakerElt)
    } yield Ok(html.detail(groupElt, speakerElt, proposals, speakers, events)(h, b))).value.map(_.getOrElse(speakerNotFound(group, speaker))).unsafeToFuture()
  }

}

object SpeakerCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", GroupRoutes.detail(group)))
      .activeFor(routes.SpeakerCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(user, group).add("Speakers" -> routes.SpeakerCtrl.list(group.slug))

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: Group, speaker: User): Breadcrumb =
    listBreadcrumb(user, group).add(speaker.name.value -> routes.SpeakerCtrl.detail(group.slug, speaker.slug))
}

