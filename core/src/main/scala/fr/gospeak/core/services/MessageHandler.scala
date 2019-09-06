package fr.gospeak.core.services

import cats.effect.IO
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.Group.Settings.Action
import fr.gospeak.core.domain.Group.Settings.Action.Trigger
import fr.gospeak.core.domain.utils.{GospeakMessage, TemplateData}
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.storage.GroupSettingsRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException

import scala.util.control.NonFatal

class MessageHandler(appConf: ApplicationConf,
                     groupSettingsRepo: GroupSettingsRepo,
                     slackSrv: SlackSrv) {
  def handle(msg: GospeakMessage): IO[Unit] = (msg match {
    case m: GospeakMessage.EventCreated => handle(m)
    case m: GospeakMessage.TalkAdded => handle(m)
    case m: GospeakMessage.TalkRemoved => handle(m)
    case m: GospeakMessage.EventPublished => handle(m)
    case m: GospeakMessage.ProposalCreated => handle(m)
  }).map(_ => ()).recover { case NonFatal(_) => () }

  private def handle(msg: GospeakMessage.EventCreated): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventCreated, TemplateData.eventCreated(msg))

  private def handle(msg: GospeakMessage.TalkAdded): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventAddTalk, TemplateData.talkAdded(msg))

  private def handle(msg: GospeakMessage.TalkRemoved): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventRemoveTalk, TemplateData.talkRemoved(msg))

  private def handle(msg: GospeakMessage.EventPublished): IO[Int] = IO.pure(0)

  private def handle(msg: GospeakMessage.ProposalCreated): IO[Int] = handleGroupEvent(msg.cfp.value.group, Trigger.OnProposalCreated, TemplateData.proposalCreated(msg))

  private def handleGroupEvent(id: Group.Id, e: Trigger, data: TemplateData): IO[Int] = for {
    settings <- groupSettingsRepo.find(id)
    results <- settings.actions.getOrElse(e, Seq()).map(exec(settings, _, data)).sequence
  } yield results.length

  private def exec(settings: Group.Settings, action: Action, data: TemplateData): IO[Unit] = action match {
    case Action.Slack(slack) => settings.accounts.slack.map(slackSrv.exec(slack, data, _, appConf.aesKey)).getOrElse(IO.raiseError(CustomException("No credentials for Slack")))
  }
}
