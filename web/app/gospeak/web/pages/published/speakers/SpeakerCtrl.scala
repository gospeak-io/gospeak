package gospeak.web.pages.published.speakers

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.{Proposal, Talk, User}
import gospeak.core.services.storage._
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl
import gospeak.web.pages.published.speakers.SpeakerCtrl._
import gospeak.web.utils.UICtrl
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  userRepo: PublicUserRepo,
                  talkRepo: PublicTalkRepo,
                  proposalRepo: PublicProposalRepo,
                  externalProposalRepo: PublicExternalProposalRepo,
                  groupRepo: PublicGroupRepo) extends UICtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction { implicit req =>
    userRepo.listPublic(params).map(speakers => Ok(html.list(speakers)(listBreadcrumb())))
  }

  def detail(user: User.Slug): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      speakerElt <- OptionT(userRepo.findPublic(user))
      groups <- OptionT.liftF(groupRepo.listFull(speakerElt.id))
      talks <- OptionT.liftF(talkRepo.listAll(speakerElt.id, Talk.Status.Public))
      proposals <- OptionT.liftF(externalProposalRepo.listAllCommon(speakerElt.id, Proposal.Status.Accepted))
      users <- OptionT.liftF(userRepo.list((groups.flatMap(_.owners.toList) ++ talks.flatMap(_.users)).distinct))
      res = Ok(html.detail(speakerElt, groups, talks, proposals.groupBy(_.talk.id), users)(breadcrumb(speakerElt)))
    } yield res).value.map(_.getOrElse(publicUserNotFound(user)))
  }
}

object SpeakerCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Speakers" -> routes.SpeakerCtrl.list())

  def breadcrumb(speaker: User): Breadcrumb =
    listBreadcrumb().add(speaker.name.value -> routes.SpeakerCtrl.detail(speaker.slug))
}
