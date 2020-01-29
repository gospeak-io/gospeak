package gospeak.web.pages.orga.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Group, User}
import gospeak.core.services.storage.{OrgaEventRepo, OrgaGroupRepo, OrgaProposalRepo, OrgaUserRepo}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.orga.GroupCtrl
import gospeak.web.pages.orga.speakers.SpeakerCtrl._
import gospeak.web.utils.{OrgaReq, UICtrl}
import gospeak.libs.scala.domain.Page
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: OrgaUserRepo,
                  val groupRepo: OrgaGroupRepo,
                  eventRepo: OrgaEventRepo,
                  proposalRepo: OrgaProposalRepo) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    userRepo.speakers(params).map(speakers => Ok(html.list(speakers)(listBreadcrumb)))
  }

  def detail(group: Group.Slug, speaker: User.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      speakerElt <- OptionT(userRepo.find(speaker))
      proposals <- OptionT.liftF(proposalRepo.listFull(speakerElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.users)))
      userRatings <- OptionT.liftF(proposalRepo.listRatings(proposals.items.map(_.id)))
      res = Ok(html.detail(speakerElt, proposals, speakers, userRatings)(breadcrumb(speakerElt)))
    } yield res).value.map(_.getOrElse(speakerNotFound(group, speaker)))
  }
}

object SpeakerCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Speakers" -> routes.SpeakerCtrl.list(req.group.slug))

  def breadcrumb(speaker: User)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(speaker.name.value -> routes.SpeakerCtrl.detail(req.group.slug, speaker.slug))
}
