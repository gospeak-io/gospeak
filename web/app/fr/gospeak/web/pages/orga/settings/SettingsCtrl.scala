package fr.gospeak.web.pages.orga.settings

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.storage.OrgaGroupRepo
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.settings.SettingsCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SettingsCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   groupRepo: OrgaGroupRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      b = listBreadcrumb(groupElt)
    } yield Ok(html.list(groupElt)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object SettingsCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Settings" -> routes.SettingsCtrl.list(group.slug))

  def breadcrumb(group: Group, setting: (String, Call)): Breadcrumb =
    listBreadcrumb(group).add(setting._1 -> setting._2)
}
