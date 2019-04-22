package fr.gospeak.web.pages.published.groups

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.{PublicCfpRepo, PublicGroupRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                groupRepo: PublicGroupRepo,
                cfpRepo: PublicCfpRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groups <- groupRepo.listPublic(params)
    } yield Ok(html.list(groups))).unsafeToFuture()
  }

  def detail(group: Group.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.findPublic(group))
      cfps <- OptionT.liftF(cfpRepo.listAllOpen(groupElt.id, now))
    } yield Ok(html.detail(groupElt, cfps))).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }
}
