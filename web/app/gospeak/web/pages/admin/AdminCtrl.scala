package gospeak.web.pages.admin

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.messages.Message
import gospeak.core.domain.{Event, Group}
import gospeak.core.services.slack.domain.SlackAction
import gospeak.core.services.storage._
import gospeak.libs.scala.domain.{Mustache, Page}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.pages.admin.AdminCtrl.UserTemplateReport
import gospeak.web.services.MessageSrv
import gospeak.web.utils.UICtrl
import io.circe.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import gospeak.libs.scala.Extensions._

class AdminCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                groupRepo: AdminGroupRepo,
                groupSettingsRepo: AdminGroupSettingsRepo,
                eventRepo: AdminEventRepo,
                extEventRepo: AdminExternalEventRepo,
                videoRepo: AdminVideoRepo,
                ms: MessageSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.AdminAction {
  def index(): Action[AnyContent] = AdminAction { implicit req =>
    IO.pure(Ok(html.index()))
  }

  def checkUserTemplates(params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      groups <- groupRepo.list(params)
      settings <- groupSettingsRepo.list(groups.items.map(_.id)).map(_.groupBy(_._1).mapValues(_.map(_._2)))
      events <- eventRepo.listAllFromGroups(groups.items.map(_.id)).map(_.groupBy(_.group))
      eventInfo = ms.sample(Some(Message.Ref.eventInfo))
      triggerData = Group.Settings.Action.Trigger.all.map(t => (t, ms.sample(Some(t.message)))).toMap
      reports = groups.map(g => UserTemplateReport(g, settings.getOrElse(g.id, List()), events.getOrElse(g.id, List()), eventInfo, triggerData))
      defaultEventDescription = req.conf.gospeak.event.description.render(eventInfo)
    } yield Ok(html.checkUserTemplates(reports, defaultEventDescription))
  }

  def fetchVideos(params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      extEvents <- extEventRepo.list(params.withFilter("video", "true"))
      extEventsWithVideoCount <- extEvents.map(e => e.videos.map(v =>  videoRepo.count(v)).getOrElse(IO.pure(0L)).map(c => e -> c)).sequence
    } yield Ok(html.fetchVideos(extEventsWithVideoCount))
  }
}

object AdminCtrl {

  final case class UserTemplateReport(group: Group,
                                      templateCount: Int,
                                      errorCount: Int,
                                      groupSettingsActionsErrors: List[(Group.Settings.Action.Trigger, Int, String, String, Mustache.Error)],
                                      groupSettingsEventErrors: List[(String, Mustache.Error)],
                                      eventErrors: List[(Event, Mustache.Error)])

  object UserTemplateReport {
    def apply(group: Group,
              settings: List[Group.Settings],
              events: List[Event],
              eventInfo: Json,
              triggerData: Map[Group.Settings.Action.Trigger, Json]): UserTemplateReport = {
      val groupSettingsActionsTemplates = settings.flatMap(_.actions.flatMap { case (trigger, actions) =>
        actions.zipWithIndex.flatMap {
          case (action: Group.Settings.Action.Email, index) =>
            List("to" -> action.to, "subject" -> action.subject, "content" -> action.content.asText).map { case (attr, tmpl) => (trigger, index, "Email", attr, tmpl) }
          case (Group.Settings.Action.Slack(action: SlackAction.PostMessage), index) =>
            List("channel" -> action.channel, "message" -> action.message.asText).map { case (attr, tmpl) => (trigger, index, "Slack.PostMessage", attr, tmpl) }
        }.map { case (trigger, index, action, attr, tmpl) => (trigger, index, action, attr, tmpl.render(triggerData.getOrElse(trigger, Json.obj()))) }
      })
      val groupSettingsActionsErrors = groupSettingsActionsTemplates.collect { case (trigger, index, action, attr, Left(err)) => (trigger, index, action, attr, err) }

      val groupSettingsEventTemplates = settings.flatMap(_.event.allTemplates.map { case (name, _, tmpl) => (name, tmpl.asText.render(eventInfo)) })
      val groupSettingsEventErrors = groupSettingsEventTemplates.collect { case (name, Left(err)) => (name, err) }

      val eventTemplates = events.map(e => (e, e.description.render(eventInfo)))
      val eventErrors = eventTemplates.collect { case (e, Left(err)) => (e, err) }

      val templateCount = groupSettingsActionsTemplates.length + groupSettingsEventTemplates.length + eventTemplates.length
      val errorCount = groupSettingsActionsErrors.length + groupSettingsEventErrors.length + eventErrors.length
      new UserTemplateReport(group, templateCount, errorCount, groupSettingsActionsErrors, groupSettingsEventErrors, eventErrors)
    }
  }

}
