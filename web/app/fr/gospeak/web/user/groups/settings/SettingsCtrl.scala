package fr.gospeak.web.user.groups.settings

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Cfp, Group, User}
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.{Breadcrumb, HeaderInfo, NavLink}
import fr.gospeak.web.user.groups.GroupCtrl
import fr.gospeak.web.user.groups.routes.{GroupCtrl => GroupRoutes}
import fr.gospeak.web.user.groups.settings.SettingsCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

class SettingsCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   db: GospeakDb) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(db.getGroup(req.identity.user.id, group))
      cfpOpt <- OptionT.liftF(db.getCfp(groupElt.id))
      h = listHeader(group)
      b = listBreadcrumb(req.identity.user.name, group -> groupElt.name)
    } yield Ok(html.list(groupElt, cfpOpt)(h, b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def cfp(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    cfpForm(group, SettingsForms.cfpCreate).unsafeToFuture()
  }

  def cfpCreate(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    SettingsForms.cfpCreate.bindFromRequest.fold(
      formWithErrors => cfpForm(group, formWithErrors),
      data => {
        (for {
          groupElt <- OptionT(db.getGroup(req.identity.user.id, group))
          _ <- OptionT.liftF(db.createCfp(groupElt.id, data, req.identity.user.id, now))
        } yield Redirect(GroupRoutes.detail(group))).value.map(_.getOrElse(groupNotFound(group)))
      }
    ).unsafeToFuture()
  }

  private def cfpForm(group: Group.Slug, form: Form[Cfp.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(db.getGroup(req.identity.user.id, group))
      cfpOpt <- OptionT.liftF(db.getCfp(groupElt.id))
      h = header(group)
      b = breadcrumb(req.identity.user.name, group -> groupElt.name, "CFP" -> routes.SettingsCtrl.cfp(group))
    } yield Ok(html.cfp(form, groupElt, cfpOpt)(h, b))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object SettingsCtrl {
  def listHeader(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    GroupCtrl.header(group)
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.groups.routes.GroupCtrl.detail(group)))
      .activeFor(routes.SettingsCtrl.list(group))

  def listBreadcrumb(user: User.Name, group: (Group.Slug, Group.Name)): Breadcrumb =
    group match {
      case (groupSlug, _) => GroupCtrl.breadcrumb(user, group).add("Settings" -> routes.SettingsCtrl.list(groupSlug))
    }

  def header(group: Group.Slug)(implicit req: SecuredRequest[CookieEnv, AnyContent]): HeaderInfo =
    listHeader(group)

  def breadcrumb(user: User.Name, group: (Group.Slug, Group.Name), setting: (String, Call)): Breadcrumb =
    (group, setting) match {
      case (_, (settingName, settingUrl)) =>
        listBreadcrumb(user, group).add(settingName -> settingUrl)
    }
}
