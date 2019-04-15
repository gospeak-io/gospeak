package fr.gospeak.web.pages.published.cfps

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Cfp
import fr.gospeak.core.services.PublicCfpRepo
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class CfpCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              cfpRepo: PublicCfpRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    (for {
      cfps <- cfpRepo.listOpen(now, params)
    } yield Ok(html.list(cfps))).unsafeToFuture()
  }

  def detail(cfp: Cfp.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      cfpElt <- OptionT(cfpRepo.find(cfp))
    } yield Ok(html.detail(cfpElt))).value.map(_.getOrElse(publicCfpNotFound(cfp))).unsafeToFuture()
  }
}
