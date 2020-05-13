package gospeak.web.pages.published

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.services.storage._
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.domain.Breadcrumb
import gospeak.web.pages.published.HomeCtrl._
import gospeak.web.utils.UICtrl
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf,
               userRepo: PublicUserRepo,
               talkRepo: PublicTalkRepo,
               groupRepo: PublicGroupRepo,
               cfpRepo: PublicCfpRepo,
               eventRepo: PublicEventRepo,
               proposalRepo: PublicProposalRepo,
               externalCfpRepo: PublicExternalCfpRepo,
               externalEvent: PublicExternalEventRepo,
               externalProposal: PublicExternalProposalRepo) extends UICtrl(cc, silhouette, conf) {
  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.index()(breadcrumb())))
  }

  def why(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.why()(breadcrumb().add("Why use Gospeak" -> routes.HomeCtrl.why()))))
  }

  def videoNewsletter(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.videoNewsletter()(breadcrumb().add("Video newsletter" -> routes.HomeCtrl.videoNewsletter()))))
  }

  def sitemap(): Action[AnyContent] = UserAwareAction { implicit req =>
    for {
      users <- userRepo.listAllPublicSlugs().map(_.toMap)
      talks <- talkRepo.listAllPublicSlugs()
      groups <- groupRepo.listAllSlugs().map(_.toMap)
      events <- eventRepo.listAllPublishedSlugs()
      proposals <- proposalRepo.listAllPublicIds()
      cfps <- cfpRepo.listAllPublicSlugs()
      extCfps <- externalCfpRepo.listAllIds()
      extEvents <- externalEvent.listAllIds()
      extProposals <- externalProposal.listAllPublicIds()
    } yield Ok(html.sitemap(users, talks, groups, events, proposals, cfps, extCfps, extEvents, extProposals)).as("text/xml")
  }
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home", routes.HomeCtrl.index())
}
