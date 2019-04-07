package fr.gospeak.web.user.groups.settings

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.{CfpRepo, GroupRepo}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.settings.SettingsCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class SettingsCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   groupRepo: GroupRepo,
                   cfpRepo: CfpRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(req.identity.user.id, group))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, groupElt)
    } yield Ok(html.list(groupElt)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object SettingsCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.SettingsCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: Group): Breadcrumb =
      GroupCtrl.breadcrumb(user, group).add("Settings" -> routes.SettingsCtrl.list(group.slug))

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: Group, setting: (String, Call)): Breadcrumb =
    setting match {
      case (settingName, settingUrl) => listBreadcrumb(user, group).add(settingName -> settingUrl)
    }
}
