package gospeak.web.pages.admin

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain.messages.Message
import gospeak.core.domain._
import gospeak.core.services.slack.domain.SlackAction
import gospeak.core.services.storage._
import gospeak.core.services.video.VideoSrv
import gospeak.libs.scala.Diff
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Liquid, Page}
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.pages.admin.AdminCtrl.{UpdateExtEventVideoJob, UserTemplateReport}
import gospeak.web.services.{MessageSrv, SchedulerSrv}
import gospeak.web.utils.{AdminReq, GsForms, UICtrl}
import io.circe.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import java.time.Instant
import scala.collection.mutable
import scala.util.Try
import scala.util.control.NonFatal

class AdminCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                adminRepo: AdminRepo,
                userRepo: AdminUserRepo,
                groupRepo: AdminGroupRepo,
                groupSettingsRepo: AdminGroupSettingsRepo,
                eventRepo: AdminEventRepo,
                extEventRepo: AdminExternalEventRepo,
                videoRepo: AdminVideoRepo,
                videoSrv: VideoSrv,
                schedulerSrv: SchedulerSrv,
                ms: MessageSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.AdminAction {
  def index(): Action[AnyContent] = AdminAction { implicit req =>
    IO.pure(Ok(html.index()))
  }

  def userAccounts(params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      users <- userRepo.list(params)
    } yield Ok(html.userAccounts(users))
  }

  def deleteUser(id: User.Id, params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      _ <- userRepo.delete(id)
    } yield Redirect(routes.AdminCtrl.userAccounts(params)).flashing("success" -> "User deleted!")
  }

  def deleteUserPage(params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      users <- userRepo.list(params)
      _ <- users.items.map(user => userRepo.delete(user.user.id)).sequence
    } yield Redirect(routes.AdminCtrl.userAccounts(params)).flashing("success" -> s"Deleted ${users.items.length} users!")
  }

  def schedulers(): Action[AnyContent] = AdminAction { implicit req =>
    val schedulers = schedulerSrv.getSchedulers
    val execs = schedulerSrv.getExecs
    IO(Ok(html.schedulers(schedulers, execs)))
  }

  def schedulerExec(scheduler: String): Action[AnyContent] = AdminAction { implicit req =>
    val next = Redirect(routes.AdminCtrl.schedulers())
    schedulerSrv.exec(scheduler).map {
      case Some(SchedulerSrv.Exec(name, _, _, _, res, None)) =>
        next.flashing("success" -> s"Scheduler '$name' executed: $res")
      case Some(SchedulerSrv.Exec(name, _, _, _, res, Some(err))) =>
        next.flashing("error" -> s"Scheduler '$name' had an error: <b>$err</b><br>Result: $res")
      case None =>
        next.flashing("error" -> s"No scheduler with name '$scheduler'")
    }
  }

  def checkUserTemplates(params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      groups <- groupRepo.list(params)
      settings <- groupSettingsRepo.list(groups.items.map(_.id)).map(_.groupBy(_._1).mapValues(_.map(_._2)))
      events <- eventRepo.listAllFromGroups(groups.items.map(_.id)).map(_.groupBy(_.group))
      eventInfo = ms.sample(Some(Message.Ref.eventInfo))
      proposalInfo = ms.sample(Some(Message.Ref.proposalInfo))
      triggerData = Group.Settings.Action.Trigger.all.map(t => (t, ms.sample(Some(t.message)))).toMap
      reports = groups.map(g => UserTemplateReport(g, settings.getOrElse(g.id, List()), events.getOrElse(g.id, List()), eventInfo, triggerData))
      defaultTemplates = Map(
        "gospeak.event.description" -> req.conf.gospeak.event.description.render(eventInfo),
        "gospeak.proposal.tweet" -> req.conf.gospeak.proposal.tweet.render(proposalInfo)
      ).collect { case (k, Left(e)) => (k, e) }
    } yield Ok(html.userTemplatesCheck(reports, defaultTemplates))
  }

  def editUserTemplate(group: Group.Id, kind: String, ref: String, params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      (tmplOpt, templateRef) <- kind match {
        case "event" => for {
          eventId <- Event.Id.from(ref).toIO
          event <- eventRepo.find(eventId)
        } yield event.map(_.description.asText.as[Nothing]) -> Message.Ref.eventInfo
        case "template" => for {
          settings <- groupSettingsRepo.find(group)
        } yield settings.getEventTemplate(ref).map(_.as[Nothing]) -> Message.Ref.eventInfo
        case "action" => for {
          settings <- groupSettingsRepo.find(group)
          List(t, i, _, attr) = ref.split('/').toList
          trigger <- Group.Settings.Action.Trigger.from(t).toIO
          index <- Try(i.toInt).mapFailure(e => CustomException(s"Invalid index: ${e.getMessage}")).toIO
          tmpl = settings.actions.getOrElse(trigger, List())(index) match {
            case Group.Settings.Action.Email(to, _, _) if attr == "to" => Some(to.as[Nothing])
            case Group.Settings.Action.Email(_, subject, _) if attr == "subject" => Some(subject.as[Nothing])
            case Group.Settings.Action.Email(_, _, content) if attr == "content" => Some(content.asText.as[Nothing])
            case Group.Settings.Action.Slack(SlackAction.PostMessage(channel, _, _, _)) if attr == "channel" => Some(channel.as[Nothing])
            case Group.Settings.Action.Slack(SlackAction.PostMessage(_, message, _, _)) if attr == "message" => Some(message.asText.as[Nothing])
            case _ => None
          }
        } yield tmpl -> trigger.message
      }
      notFound = redirectToPreviousPageOr(routes.AdminCtrl.checkUserTemplates()).flashing("warning" -> s"$kind/$ref not found :(")
    } yield tmplOpt.map(tmpl => Ok(html.userTemplateEdit(group, kind, ref, GsForms.templateForm.fill(tmpl), templateRef, params))).getOrElse(notFound)
  }

  def doEditUserTemplate(group: Group.Id, kind: String, ref: String, params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    val next = Redirect(routes.AdminCtrl.checkUserTemplates(params))
    GsForms.templateForm.bindFromRequest.fold(
      formWithErrors => kind match {
        case "event" | "template" => IO.pure(Ok(html.userTemplateEdit(group, kind, ref, formWithErrors, Message.Ref.eventInfo, params)))
        case "action" =>
          val List(t, _, _, _) = ref.split('/').toList
          Group.Settings.Action.Trigger.from(t).toIO.map { trigger =>
            Ok(html.userTemplateEdit(group, kind, ref, formWithErrors, trigger.message, params))
          }
      },
      tmpl => kind match {
        case "event" => for {
          eventId <- Event.Id.from(ref).toIO
          event <- eventRepo.find(eventId).flatMap(_.toIO(CustomException(s"Event ${eventId.value} not found")))
          _ <- eventRepo.editDescription(eventId, tmpl.asMarkdown.as[Message.EventInfo])
        } yield next.flashing("success" -> s"Description updated for <b>${event.name.value}</b>")
        case "template" => for {
          groupElt <- groupRepo.find(group).flatMap(_.toIO(CustomException(s"Group ${group.value} not found")))
          settings <- groupSettingsRepo.find(group)
          updatedSettings <- settings.updateEventTemplate(ref, ref, tmpl.as[Message.EventInfo]).toIO
          _ <- groupSettingsRepo.set(group, updatedSettings)
        } yield next.flashing("success" -> s"Event template '<b>$ref</b>' updated for <b>${groupElt.name.value}</b> group")
        case "action" => for {
          groupElt <- groupRepo.find(group).flatMap(_.toIO(CustomException(s"Group ${group.value} not found")))
          settings <- groupSettingsRepo.find(group)
          List(t, i, action, attr) = ref.split('/').toList
          trigger <- Group.Settings.Action.Trigger.from(t).toIO
          index <- Try(i.toInt).mapFailure(e => CustomException(s"Invalid index: ${e.getMessage}")).toIO
          updatedSettings <- settings.getAction(trigger, index).map {
            case Group.Settings.Action.Email(_, subject, content) if attr == "to" => Group.Settings.Action.Email(tmpl.as[Any], subject, content)
            case Group.Settings.Action.Email(to, _, content) if attr == "subject" => Group.Settings.Action.Email(to, tmpl.as[Any], content)
            case Group.Settings.Action.Email(to, subject, _) if attr == "content" => Group.Settings.Action.Email(to, subject, tmpl.asMarkdown.as[Any])
            case Group.Settings.Action.Slack(SlackAction.PostMessage(_, message, c, i)) if attr == "channel" => Group.Settings.Action.Slack(SlackAction.PostMessage(tmpl.as[Any], message, c, i))
            case Group.Settings.Action.Slack(SlackAction.PostMessage(channel, _, c, i)) if attr == "message" => Group.Settings.Action.Slack(SlackAction.PostMessage(channel, tmpl.asMarkdown.as[Any], c, i))
            case action => action
          }.map(settings.updateAction(trigger, index)(trigger, _)).toIO(CustomException(s"Action $i on $t not found"))
          _ <- groupSettingsRepo.set(group, updatedSettings)
        } yield next.flashing("success" -> s"$attr attribute updated for $action on $t for <b>${groupElt.name.value}</b> group")
      }
    )
  }

  private val updateExtEventVideosJobs = mutable.HashMap[ExternalEvent.Id, UpdateExtEventVideoJob]()

  def fetchVideos(params: Page.Params): Action[AnyContent] = AdminAction { implicit req =>
    for {
      // TODO talks with video
      // TODO proposals with video
      // TODO external proposals with video
      extEvents <- extEventRepo.list(params.addFilter("video", "true"))
      extEventsWithVideoCount <- extEvents.map(e => videoRepo.count(e.id).map(c => e -> c)).sequence
      jobs = updateExtEventVideosJobs.values.toList
      _ = updateExtEventVideosJobs.filter(_._2.finished.isDefined).foreach { case (id, _) => updateExtEventVideosJobs.remove(id) }
    } yield Ok(html.fetchVideos(extEventsWithVideoCount, jobs))
  }

  def updateExtEventVideos(event: ExternalEvent.Id): Action[AnyContent] = AdminAction { implicit req =>
    val next = redirectToPreviousPageOr(routes.AdminCtrl.fetchVideos())
    extEventRepo.find(event).flatMap(_.toIO(CustomException(s"No external event for id $event"))).map { eventElt =>
      if (updateExtEventVideosJobs.contains(event)) {
        next.flashing("error" -> s"Job for ${eventElt.name.value} already started.")
      } else {
        updateExtEventVideosJob(eventElt).unsafeRunAsyncAndForget()
        next.flashing("success" -> s"Start job for ${eventElt.name.value}.")
      }
    }
  }

  private def updateExtEventVideosJob(event: ExternalEvent)(implicit req: AdminReq[AnyContent]): IO[Unit] = {
    val job = UpdateExtEventVideoJob(event, Instant.now())
    updateExtEventVideosJobs.put(event.id, job)
    (for {
      url <- event.videos.toIO(CustomException(s"No videos for external event ${event.name.value}"))
      gospeakVideos <- videoRepo.listAll(event.id)
      _ = job.gospeakVideos = Some(gospeakVideos)
      remoteVideos <- videoSrv.listVideos(url)
      _ = job.remoteVideos = Some(remoteVideos)
      videosDiff = Diff.from[Video.Data](gospeakVideos.map(_.data), remoteVideos, (a: Video.Data, b: Video.Data) => a.url == b.url)
      _ = job.diff = Some(videosDiff)
      _ <- videosDiff.rightOnly.map(v => videoRepo.create(v, event.id)).sequence
      _ = job.created = Some(Instant.now())
      _ <- videosDiff.both.map { case (_, v) => videoRepo.edit(v, event.id) }.sequence
      _ = job.updated = Some(Instant.now())
      _ <- videosDiff.leftOnly.map(v => videoRepo.remove(v, event.id)).sequence
      _ = job.finished = Some(Instant.now())
    } yield ()).recover { case NonFatal(e) =>
      job.error = Some(e)
      job.finished = Some(Instant.now())
    }
  }

  def dbStats(since: Option[Instant]): Action[AnyContent] = AdminAction { implicit req =>
    for {
      stats <- adminRepo.getStats(since)
    } yield Ok(html.dbStats(stats))
  }
}

object AdminCtrl {

  final case class UserTemplateReport(group: Group,
                                      templateCount: Int,
                                      errorCount: Int,
                                      groupSettingsActionsErrors: List[(Group.Settings.Action.Trigger, Int, String, String, Liquid.Error)],
                                      groupSettingsEventErrors: List[(String, Liquid.Error)],
                                      eventErrors: List[(Event, Liquid.Error)])

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

      val groupSettingsEventTemplates = settings.flatMap(_.event.allTemplates.map { case (name, _, tmpl) => (name, tmpl.render(eventInfo)) })
      val groupSettingsEventErrors = groupSettingsEventTemplates.collect { case (name, Left(err)) => (name, err) }

      val eventTemplates = events.map(e => (e, e.description.render(eventInfo)))
      val eventErrors = eventTemplates.collect { case (e, Left(err)) => (e, err) }

      val templateCount = groupSettingsActionsTemplates.length + groupSettingsEventTemplates.length + eventTemplates.length
      val errorCount = groupSettingsActionsErrors.length + groupSettingsEventErrors.length + eventErrors.length
      new UserTemplateReport(group, templateCount, errorCount, groupSettingsActionsErrors, groupSettingsEventErrors, eventErrors)
    }
  }

  final case class UpdateExtEventVideoJob(event: ExternalEvent,
                                          started: Instant,
                                          var gospeakVideos: Option[List[Video]] = None,
                                          var remoteVideos: Option[List[Video.Data]] = None,
                                          var diff: Option[Diff[Video.Data]] = None,
                                          var created: Option[Instant] = None,
                                          var updated: Option[Instant] = None,
                                          var finished: Option[Instant] = None,
                                          var error: Option[Throwable] = None)

}
