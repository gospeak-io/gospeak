package fr.gospeak.web.pages.published.groups

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.storage.{PublicCfpRepo, PublicGroupRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.published.groups.GroupCtrl._
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
      b = listBreadcrumb()
    } yield Ok(html.list(groups)(b))).unsafeToFuture()
  }

  def detail(group: Group.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.findPublic(group))
      cfps <- OptionT.liftF(cfpRepo.listAllOpen(groupElt.id, now))
      b = breadcrumb(groupElt)
    } yield Ok(html.detail(groupElt, cfps)(b))).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Groups" -> routes.GroupCtrl.list())

  def breadcrumb(group: Group): Breadcrumb =
    listBreadcrumb().add(group.name.value -> routes.GroupCtrl.detail(group.slug))
}