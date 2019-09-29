package fr.gospeak.web.pages.published.speakers

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Talk, User}
import fr.gospeak.core.services.storage.{PublicGroupRepo, PublicProposalRepo, PublicTalkRepo, PublicUserRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.published.speakers.SpeakerCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SpeakerCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  userRepo: PublicUserRepo,
                  talkRepo: PublicTalkRepo,
                  proposalRepo: PublicProposalRepo,
                  groupRepo: PublicGroupRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      speakers <- userRepo.listPublic(params)
      b = listBreadcrumb()
    } yield Ok(html.list(speakers)(b))).unsafeToFuture()
  }

  def detail(user: User.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      speakerElt <- OptionT(userRepo.findPublic(user))
      publicTalks <- OptionT.liftF(talkRepo.list(speakerElt.id, Talk.Status.Public, params))
      acceptedProposals <- OptionT.liftF(proposalRepo.listPublicFull(speakerElt.id, params))
      groups <- OptionT.liftF(groupRepo.list(speakerElt.id))
      b = breadcrumb(speakerElt)
    } yield Ok(html.detail(speakerElt, publicTalks, acceptedProposals, groups, Instant.now())(b))).value.map(_.getOrElse(publicUserNotFound(user))).unsafeToFuture()
  }
}

object SpeakerCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Speakers" -> routes.SpeakerCtrl.list())

  def breadcrumb(speaker: User): Breadcrumb =
    listBreadcrumb().add(speaker.name.value -> routes.SpeakerCtrl.detail(speaker.slug))
}
