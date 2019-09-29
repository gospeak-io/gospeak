package fr.gospeak.web.pages.orga.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.{OrgaEventRepo, OrgaGroupRepo, OrgaProposalRepo, OrgaUserRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
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
      groupElt <- OptionT(groupRepo.find(user, group))
      speakers <- OptionT.liftF(userRepo.speakers(groupElt.id, params))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt, speakers)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def detail(group: Group.Slug, speaker: User.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      speakerElt <- OptionT(userRepo.find(speaker))
      proposals <- OptionT.liftF(proposalRepo.listFull(groupElt.id, speakerElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.proposal.users)))
      b = breadcrumb(groupElt, speakerElt)
    } yield Ok(html.detail(groupElt, speakerElt, proposals, speakers)(b))).value.map(_.getOrElse(speakerNotFound(group, speaker))).unsafeToFuture()
  }
}

object SpeakerCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Speakers" -> routes.SpeakerCtrl.list(group.slug))

  def breadcrumb(group: Group, speaker: User): Breadcrumb =
    listBreadcrumb(group).add(speaker.name.value -> routes.SpeakerCtrl.detail(group.slug, speaker.slug))
}
