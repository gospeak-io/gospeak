package fr.gospeak.web.pages.orga.settings

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, User, UserRequest}
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.meetup.domain.{MeetupCredentials, MeetupException, MeetupGroup}
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackCredentials
import fr.gospeak.core.services.storage.{GroupSettingsRepo, OrgaGroupRepo, OrgaUserRepo, OrgaUserRequestRepo}
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.GroupCtrl
import fr.gospeak.web.pages.orga.settings.SettingsCtrl._
import fr.gospeak.web.pages.orga.settings.SettingsForms.{AddAction, EventTemplateItem, MeetupAccount}
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{GenericForm, OrgaReq, UICtrl}
import play.api.data.Form
import play.api.mvc._
import play.twirl.api.HtmlFormat

import scala.util.control.NonFatal

class SettingsCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   env: ApplicationConf.Env,
                   appConf: ApplicationConf,
                   val groupRepo: OrgaGroupRepo,
                   groupSettingsRepo: GroupSettingsRepo,
                   userRepo: OrgaUserRepo,
                   userRequestRepo: OrgaUserRequestRepo,
                   emailSrv: EmailSrv,
                   meetupSrv: MeetupSrv,
                   slackSrv: SlackSrv) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def settings(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    groupSettingsRepo.find.flatMap(settingsView(_))
  })

  def createAction(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    IO.pure(Ok(createActionView(SettingsForms.addAction)))
  })

  def doCreateAction(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      res <- SettingsForms.addAction.bindFromRequest.fold(
        formWithErrors => IO.pure(BadRequest(createActionView(formWithErrors))),
        data => groupSettingsRepo.set(createActionToSettings(settings, data))
          .map(_ => Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> "Action created"))
      )
    } yield res
  })

  private def createActionView(actionForm: Form[AddAction])(implicit req: OrgaReq[AnyContent]): HtmlFormat.Appendable = {
    val b = breadcrumb("Create action" -> routes.SettingsCtrl.createAction(req.group.slug))
    html.actionCreate(actionForm)(b)
  }

  def updateAction(group: Group.Slug, trigger: Group.Settings.Action.Trigger, index: Int): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      settings <- OptionT.liftF(groupSettingsRepo.find)
      action <- OptionT(IO.pure(settings.actions.get(trigger).flatMap(_.lift(index))))
      res = Ok(updateActionView(trigger, index, SettingsForms.addAction.fill(AddAction(trigger, action))))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  })

  def doUpdateAction(group: Group.Slug, trigger: Group.Settings.Action.Trigger, index: Int): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      res <- SettingsForms.addAction.bindFromRequest.fold(
        formWithErrors => IO.pure(BadRequest(updateActionView(trigger, index, formWithErrors))),
        data => groupSettingsRepo.set(updateActionToSettings(settings, trigger, index, data))
          .map(_ => Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> "Action updated"))
      )
    } yield res
  })

  private def updateActionView(trigger: Group.Settings.Action.Trigger, index: Int, actionForm: Form[AddAction])(implicit req: OrgaReq[AnyContent]): HtmlFormat.Appendable = {
    val b = breadcrumb("Update action" -> routes.SettingsCtrl.createAction(req.group.slug))
    html.actionUpdate(trigger, index, actionForm)(b)
  }

  def doRemoveAction(group: Group.Slug, trigger: Group.Settings.Action.Trigger, index: Int): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      _ <- groupSettingsRepo.set(removeActionToSettings(settings, trigger, index))
    } yield Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> "Action removed")
  })

  def meetupAuthorize(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    SettingsForms.meetupAccount.bindFromRequest.fold(
      formWithErrors => groupSettingsRepo.find.flatMap(settingsView(_, meetup = Some(formWithErrors))),
      data => {
        val redirectUri = routes.SettingsCtrl.meetupCallback(group, data.group).absoluteURL(meetupSrv.hasSecureCallback)
        meetupSrv.buildAuthorizationUrl(redirectUri).map(url => Redirect(url.value)).toIO
      }
    )
  })

  def meetupCallback(group: Group.Slug, meetupGroup: MeetupGroup.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val redirectUri = routes.SettingsCtrl.meetupCallback(group, meetupGroup).absoluteURL(meetupSrv.hasSecureCallback)
    req.getQueryString("code").map { code =>
      (for {
        settings <- groupSettingsRepo.find
        token <- meetupSrv.requestAccessToken(redirectUri, code, appConf.aesKey)
        loggedUser <- meetupSrv.getLoggedUser(appConf.aesKey)(token)
        meetupGroupElt <- meetupSrv.getGroup(meetupGroup, appConf.aesKey)(token)
        creds = MeetupCredentials(token, loggedUser, meetupGroupElt)
        _ <- groupSettingsRepo.set(settings.set(creds))
        next = Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> s"Connected to <b>${loggedUser.name}</b> meetup account")
      } yield next).recoverWith {
        case e: MeetupException => IO.pure(Redirect(routes.SettingsCtrl.settings(group)).flashing("error" -> e.getMessage))
      }
    }.getOrElse {
      val state = req.getQueryString("state")
      val error = req.getQueryString("error")
      val msg = s"Failed to authenticate with meetup${error.map(e => s", reason: $e").getOrElse("")}${state.map(s => s" (state: $s)").getOrElse("")}"
      IO.pure(Redirect(routes.SettingsCtrl.settings(group)).flashing("error" -> msg))
    }
  })

  def updateSlackAccount(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      res <- SettingsForms.slackAccount(appConf.aesKey).bindFromRequest.fold(
        formWithErrors => settingsView(settings, slack = Some(formWithErrors)),
        creds => slackSrv.getInfos(creds.token, appConf.aesKey)
          .flatMap(_ => groupSettingsRepo.set(settings.set(creds)))
          .map(_ => Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> "Slack account updated"))
          .recover { case NonFatal(e) => Redirect(routes.SettingsCtrl.settings(group)).flashing("error" -> s"Invalid Slack token: ${e.getMessage}") }
      )
    } yield res
  })

  def removeAccount(group: Group.Slug, kind: String): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      updated <- settings.removeAccount(kind).toIO
      _ <- groupSettingsRepo.set(updated)
    } yield Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> s"${kind.capitalize} account removed")
  })

  def updateEventTemplate(group: Group.Slug, templateId: Option[String]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      settings <- OptionT.liftF(groupSettingsRepo.find)
      template <- templateId.map(id => OptionT.fromOption[IO](settings.event.getTemplate(id)).map(t => Some(EventTemplateItem(id, t.asMarkdown))))
        .getOrElse(OptionT.pure[IO](None))
      form = template.map(SettingsForms.eventTemplateItem.fill).getOrElse(SettingsForms.eventTemplateItem)
    } yield updateEventTemplateView(templateId, settings, form))
      .value.map(_.getOrElse(groupNotFound(group)))
  })

  def doUpdateEventTemplate(group: Group.Slug, templateId: Option[String]): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      res <- SettingsForms.eventTemplateItem.bindFromRequest.fold(
        formWithErrors => IO.pure(updateEventTemplateView(templateId, settings, formWithErrors)),
        data => templateId.map(id => settings.updateEventTemplate(id, data.id, data.template)).getOrElse(settings.addEventTemplate(data.id, data.template.asText)).fold(
          e => IO.pure(updateEventTemplateView(templateId, settings, SettingsForms.eventTemplateItem.bindFromRequest.withGlobalError(e.getMessage))),
          updated => groupSettingsRepo.set(updated)
            .map(_ => Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> s"Template '${data.id}' updated for events"))
        )
      )
    } yield res
  })

  def doRemoveEventTemplate(group: Group.Slug, templateId: String): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      settings <- groupSettingsRepo.find
      updated <- settings.removeEventTemplate(templateId).toIO
      _ <- groupSettingsRepo.set(updated)
    } yield Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> s"Template '$templateId' removed for events")
  })

  def inviteOrga(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    val next = Redirect(routes.SettingsCtrl.settings(group))
    GenericForm.invite.bindFromRequest.fold(
      formWithErrors => IO.pure(next.flashing("error" -> req.formatErrors(formWithErrors))),
      email => for {
        invite <- userRequestRepo.invite(email)
        _ <- emailSrv.send(Emails.inviteOrgaToGroup(invite))
      } yield next.flashing("success" -> s"<b>$email</b> is invited as orga")
    )
  })

  def cancelInviteOrga(group: Group.Slug, request: UserRequest.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    for {
      invite <- userRequestRepo.cancelGroupInvite(request)
      _ <- emailSrv.send(Emails.inviteOrgaToGroupCanceled(invite))
    } yield Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> s"Invitation to <b>${invite.email.value}</b> has been canceled")
  })

  def doRemoveOrga(group: Group.Slug, orga: User.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      orgaElt <- OptionT(userRepo.find(orga))
      _ <- OptionT.liftF(groupRepo.removeOwner(orgaElt.id))
      _ <- OptionT.liftF(emailSrv.send(Emails.orgaRemovedFromGroup(orgaElt)))
      next = if (req.user.slug == orga) Redirect(UserRoutes.index()).flashing("success" -> s"You removed yourself from <b>${req.group.name.value}</b> group")
      else Redirect(routes.SettingsCtrl.settings(group)).flashing("success" -> s"You removed <b>${orgaElt.name.value}</b> from  <b>${req.group.name.value}</b> group")
    } yield next).value.map(_.getOrElse(groupNotFound(group)))
      .recover { case NonFatal(e) => Redirect(routes.SettingsCtrl.settings(group)).flashing("error" -> s"Error: ${e.getMessage}") }
  })

  private def settingsView(settings: Group.Settings,
                           meetup: Option[Form[MeetupAccount]] = None,
                           slack: Option[Form[SlackCredentials]] = None)
                          (implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    for {
      orgas <- userRepo.list(req.group.owners.toList)
      invites <- userRequestRepo.listPendingInvites
    } yield Ok(html.settings(
      settings,
      orgas,
      invites,
      meetup.getOrElse(settings.accounts.meetup.map(s => SettingsForms.meetupAccount.fill(MeetupAccount(s.group))).getOrElse(SettingsForms.meetupAccount)),
      slack.getOrElse(settings.accounts.slack.map(s => SettingsForms.slackAccount(appConf.aesKey).fill(s)).getOrElse(SettingsForms.slackAccount(appConf.aesKey))),
      GenericForm.invite
    )(listBreadcrumb))
  }

  private def updateEventTemplateView(templateId: Option[String],
                                      settings: Group.Settings,
                                      form: Form[EventTemplateItem])
                                     (implicit req: OrgaReq[AnyContent]): Result = {
    val b = listBreadcrumb.add(
      "Event" -> routes.SettingsCtrl.settings(req.group.slug),
      "Templates" -> routes.SettingsCtrl.settings(req.group.slug),
      templateId.getOrElse("New") -> routes.SettingsCtrl.updateEventTemplate(req.group.slug, templateId))
    Ok(html.updateEventTemplate(templateId, settings, form)(b))
  }

  private def createActionToSettings(settings: Group.Settings, addAction: SettingsForms.AddAction): Group.Settings = {
    val actions = settings.actions.getOrElse(addAction.trigger, Seq()) :+ addAction.action
    settings.copy(actions = settings.actions + (addAction.trigger -> actions))
  }

  private def updateActionToSettings(settings: Group.Settings, trigger: Group.Settings.Action.Trigger, index: Int, addAction: SettingsForms.AddAction): Group.Settings = {
    if (addAction.trigger == trigger) {
      val actions = settings.actions.getOrElse(addAction.trigger, Seq()).zipWithIndex.map { case (a, i) => if (i == index) addAction.action else a }
      settings.copy(actions = settings.actions + (addAction.trigger -> actions))
    } else {
      createActionToSettings(removeActionToSettings(settings, trigger, index), addAction)
    }
  }

  private def removeActionToSettings(settings: Group.Settings, trigger: Group.Settings.Action.Trigger, index: Int): Group.Settings = {
    val actions = settings.actions.getOrElse(trigger, Seq()).zipWithIndex.filter(_._2 != index).map(_._1)
    settings.copy(actions = (settings.actions + (trigger -> actions)).filter(_._2.nonEmpty))
  }
}

object SettingsCtrl {
  def listBreadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    GroupCtrl.breadcrumb.add("Settings" -> routes.SettingsCtrl.settings(req.group.slug))

  def breadcrumb(setting: (String, Call))(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb.add(setting._1 -> setting._2)
}
