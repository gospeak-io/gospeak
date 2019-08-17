package fr.gospeak.web.pages.orga.settings

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.meetup.domain.{MeetupCredentials, MeetupException, MeetupGroup}
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.core.services.storage.{OrgaGroupRepo, SettingsRepo}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.settings.SettingsCtrl._
import fr.gospeak.web.pages.orga.settings.SettingsForms.{EventTemplateItem, MeetupAccount}
import fr.gospeak.web.utils.UICtrl
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

/*
  TODO
    - encode meetup accessToken & refreshToken (in MeetupCredentials)
    - encode slack token (in SlackCredentials)
 */
class SettingsCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   groupRepo: OrgaGroupRepo,
                   settingsRepo: SettingsRepo,
                   meetupSrv: MeetupSrv,
                   slackSrv: SlackSrv) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res = settingsView(groupElt, settings)
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def meetupAuthorize(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(SettingsForms.meetupAccount.bindFromRequest.fold(
        formWithErrors => IO.pure(settingsView(groupElt, settings, meetup = Some(formWithErrors))),
        data => {
          val redirectUri = routes.SettingsCtrl.meetupCallback(group, data.group).absoluteURL(meetupSrv.hasSecureCallback)
          meetupSrv.buildAuthorizationUrl(redirectUri).map(url => Redirect(url.value)).toIO
        }))
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def meetupCallback(group: Group.Slug, meetupGroup: MeetupGroup.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val redirectUri = routes.SettingsCtrl.meetupCallback(group, meetupGroup).absoluteURL(meetupSrv.hasSecureCallback)
    req.getQueryString("code").map { code =>
      (for {
        groupElt <- OptionT(groupRepo.find(user, group))
        settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
        token <- OptionT.liftF(meetupSrv.requestAccessToken(redirectUri, code))
        loggedUser <- OptionT.liftF(meetupSrv.getLoggedUser()(token))
        meetupGroupElt <- OptionT.liftF(meetupSrv.getGroup(meetupGroup)(token))
        creds = MeetupCredentials(token, loggedUser, meetupGroupElt)
        _ <- OptionT.liftF(settingsRepo.set(groupElt.id, settings.set(creds), user, now))
        next = Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> s"Connected to <b>${loggedUser.name}</b> meetup account")
      } yield next).value.map(_.getOrElse(groupNotFound(group))).recoverWith {
        case e: MeetupException => IO.pure(Redirect(routes.SettingsCtrl.list(group)).flashing("error" -> e.getMessage))
      }
    }.getOrElse {
      val state = req.getQueryString("state")
      val error = req.getQueryString("error")
      val msg = s"Failed to authenticate with meetup${error.map(e => s", reason: $e").getOrElse("")}${state.map(s => s" (state: $s)").getOrElse("")}"
      IO.pure(Redirect(routes.SettingsCtrl.list(group)).flashing("error" -> msg))
    }.unsafeToFuture()
  }

  def updateSlackAccount(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(SettingsForms.slackAccount.bindFromRequest.fold(
        formWithErrors => IO.pure(settingsView(groupElt, settings, slack = Some(formWithErrors))),
        creds => slackSrv.getInfos(creds.token)
          .flatMap(_ => settingsRepo.set(groupElt.id, settings.set(creds), user, now))
          .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> "Slack account updated"))
          .recover { case NonFatal(e) => Redirect(routes.SettingsCtrl.list(group)).flashing("error" -> s"Invalid Slack token: ${e.getMessage}") }
      ))
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def removeAccount(group: Group.Slug, kind: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      updated <- OptionT.liftF(settings.removeAccount(kind).toIO)
      res <- OptionT.liftF(settingsRepo.set(groupElt.id, updated, user, now)
        .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> s"${kind.capitalize} account removed"))
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

  def updateEventTemplate(group: Group.Slug, templateId: Option[String]): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      template <- templateId.map(id => OptionT.fromOption[IO](settings.event.getTemplate(id)).map(t => Some(EventTemplateItem(id, t.asMarkdown))))
        .getOrElse(OptionT.pure[IO](None))
      form = template.map(SettingsForms.eventTemplateItem.fill).getOrElse(SettingsForms.eventTemplateItem)
    } yield updateEventTemplateView(groupElt, templateId, settings, form))
      .value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def doUpdateEventTemplate(group: Group.Slug, templateId: Option[String]): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      res <- OptionT.liftF(SettingsForms.eventTemplateItem.bindFromRequest.fold(
        formWithErrors => IO.pure(updateEventTemplateView(groupElt, templateId, settings, formWithErrors)),
        data => templateId.map(id => settings.updateEventTemplate(id, data.id, data.template)).getOrElse(settings.addEventTemplate(data.id, data.template.asText)).fold(
          e => IO.pure(updateEventTemplateView(groupElt, templateId, settings, SettingsForms.eventTemplateItem.bindFromRequest.withGlobalError(e.getMessage))),
          updated => settingsRepo.set(groupElt.id, updated, user, now)
            .map(_ => Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> s"Template '${data.id}' updated for events"))
        )
      ))
    } yield res).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def doRemoveEventTemplate(group: Group.Slug, templateId: String): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(routes.SettingsCtrl.list(group)).flashing("success" -> s"Template '$templateId' removed for events")
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      settings <- OptionT.liftF(settingsRepo.find(groupElt.id))
      updated <- OptionT.liftF(settings.removeEventTemplate(templateId).toIO)
      _ <- OptionT.liftF(settingsRepo.set(groupElt.id, updated, user, now))
    } yield next).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  private def settingsView(groupElt: Group,
                           settings: Group.Settings,
                           meetup: Option[Form[MeetupAccount]] = None,
                           slack: Option[Form[SlackCredentials]] = None,
                           action: Option[Form[SettingsForms.AddAction]] = None)
                          (implicit req: SecuredRequest[CookieEnv, AnyContent]): Result = {
    Ok(html.list(
      groupElt,
      settings,
      meetup.getOrElse(settings.accounts.meetup.map(s => SettingsForms.meetupAccount.fill(MeetupAccount(s.group))).getOrElse(SettingsForms.meetupAccount)),
      slack.getOrElse(settings.accounts.slack.map(s => SettingsForms.slackAccount.fill(s)).getOrElse(SettingsForms.slackAccount)),
      action.getOrElse(SettingsForms.addAction)
    )(listBreadcrumb(groupElt)))
  }

  private def updateEventTemplateView(group: Group,
                                      templateId: Option[String],
                                      settings: Group.Settings,
                                      form: Form[EventTemplateItem])
                                     (implicit req: SecuredRequest[CookieEnv, AnyContent]): Result = {
    val b = listBreadcrumb(group).add(
      "Event" -> routes.SettingsCtrl.list(group.slug),
      "Templates" -> routes.SettingsCtrl.list(group.slug),
      templateId.getOrElse("New") -> routes.SettingsCtrl.updateEventTemplate(group.slug, templateId))
    Ok(html.updateEventTemplate(group, templateId, settings, form)(b))
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
