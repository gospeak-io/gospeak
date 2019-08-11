package fr.gospeak.web.pages.orga.settings

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.core.services.storage.{OrgaGroupRepo, SettingsRepo}
import fr.gospeak.libs.scalautils.domain.MarkdownTemplate
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.settings.SettingsCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class SettingsCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   groupRepo: OrgaGroupRepo,
                   settingsRepo: SettingsRepo,
                   slackSrv: SlackSrv) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
    } yield settingsView(groupElt, settings)).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def updateSlackAccount(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    import cats.implicits._
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(SettingsForms.slackAccount.bindFromRequest.fold(
        formWithErrors => IO.pure(settingsView(groupElt, settings, slack = Some(formWithErrors))),
        data => slackSrv.getInfos(data.token)
          .flatMap(_ => settingsRepo.set(groupElt.id, settings.set(data), user, now))
          .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> "Slack account updated"))
          .recover { case NonFatal(e) => Redirect(routes.SettingsCtrl.list(group)).flashing("error" -> s"Invalid Slack token: ${e.getMessage}") }
      ))
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def removeSlackAccount(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(settingsRepo.set(groupElt.id, settings.copy(accounts = settings.accounts.copy(slack = None)), user, now)
        .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> "Slack account removed"))
      )
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def addAction(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(SettingsForms.addAction.bindFromRequest.fold(
        formWithErrors => IO.pure(settingsView(groupElt, settings, action = Some(formWithErrors))),
        data => settingsRepo.set(groupElt.id, addActionToSettings(settings, data), user, now)
          .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> "Action added"))
      ))
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def removeAction(group: Group.Slug, trigger: Group.Settings.Action.Trigger, index: Int): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(settingsRepo.set(groupElt.id, removeActionToSettings(settings, trigger, index), user, now)
        .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> "Action removed"))
      )
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def updateEventTemplate(group: Group.Slug, templateId: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      template <- OptionT.fromOption[IO](settings.event.getTemplate(templateId))
      form = SettingsForms.eventTemplateSettings.fill(template)
    } yield updateEventTemplateView(groupElt, templateId, form))
      .value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def doUpdateEventTemplate(group: Group.Slug, templateId: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(SettingsForms.eventTemplateSettings.bindFromRequest.fold(
        formWithErrors => IO.pure(updateEventTemplateView(groupElt, templateId, formWithErrors)),
        data => settingsRepo.set(groupElt.id, settings.copy(event = settings.event.updateTemplate(templateId, data)), user, now)
          .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> s"Template '$templateId' updated for events"))
      ))
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  private def settingsView(groupElt: Group,
                           settings: Group.Settings,
                           slack: Option[Form[SlackCredentials]] = None,
                           action: Option[Form[SettingsForms.AddAction]] = None,
                           eventSettings: Option[Form[MarkdownTemplate[TemplateData.EventInfo]]] = None)
                          (implicit req: SecuredRequest[CookieEnv, AnyContent]): Result = {
    Ok(html.list(
      groupElt,
      settings,
      slack.getOrElse(settings.accounts.slack.map(s => SettingsForms.slackAccount.fill(s)).getOrElse(SettingsForms.slackAccount)),
      action.getOrElse(SettingsForms.addAction),
      eventSettings.getOrElse(SettingsForms.eventTemplateSettings.fill(settings.event.defaultDescription))
    )(listBreadcrumb(groupElt)))
  }

  private def updateEventTemplateView(group: Group,
                                      templateId: String,
                                      form: Form[MarkdownTemplate[TemplateData.EventInfo]])
                                     (implicit req: SecuredRequest[CookieEnv, AnyContent]): Result = {
    val b = listBreadcrumb(group).add(
      "Event" -> routes.SettingsCtrl.list(group.slug),
      "Templates" -> routes.SettingsCtrl.list(group.slug),
      templateId -> routes.SettingsCtrl.updateEventTemplate(group.slug, templateId))
    Ok(html.updateEventTemplate(group, templateId, form)(b))
  }

  private def addActionToSettings(settings: Group.Settings, addAction: SettingsForms.AddAction): Group.Settings = {
    val actions = settings.actions.getOrElse(addAction.trigger, Seq()) :+ addAction.action
    settings.copy(actions = settings.actions + (addAction.trigger -> actions))
  }

  private def removeActionToSettings(settings: Group.Settings, trigger: Group.Settings.Action.Trigger, index: Int): Group.Settings = {
    val actions = settings.actions.getOrElse(trigger, Seq()).zipWithIndex.filter(_._2 != index).map(_._1)
    settings.copy(actions = (settings.actions + (trigger -> actions)).filter(_._2.nonEmpty))
  }
}

object SettingsCtrl {
  def listBreadcrumb(group: Group): Breadcrumb =
    GroupCtrl.breadcrumb(group).add("Settings" -> routes.SettingsCtrl.list(group.slug))

  def breadcrumb(group: Group, setting: (String, Call)): Breadcrumb =
    listBreadcrumb(group).add(setting._1 -> setting._2)
}
