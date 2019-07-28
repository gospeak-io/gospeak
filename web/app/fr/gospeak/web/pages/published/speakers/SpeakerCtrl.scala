package fr.gospeak.web.pages.published.speakers

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Proposal, User}
import fr.gospeak.core.services.storage.{PublicGroupRepo, PublicProposalRepo, PublicUserRepo}
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
      acceptedProposals <- OptionT.liftF(proposalRepo.list(speakerElt.id, Proposal.Status.Accepted, params))
      groups <- OptionT.liftF(groupRepo.findPublic(speakerElt.id, params))
      b = breadcrumb(speakerElt)
    } yield Ok(html.detail(speakerElt, acceptedProposals, groups, Instant.now())(b))).value.map(_.getOrElse(publicUserNotFound(user))).unsafeToFuture()
  }
}

object SpeakerCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Speakers" -> routes.SpeakerCtrl.list())

  def breadcrumb(speaker: User): Breadcrumb =
    listBreadcrumb().add(speaker.name.value -> routes.SpeakerCtrl.detail(speaker.slug))
}
