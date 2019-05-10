package fr.gospeak.core.services

import cats.effect.IO
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.Group.Settings.Events
import fr.gospeak.core.domain.Group.Settings.Events.Event
import fr.gospeak.core.domain.utils.{GospeakMessage, TemplateData}
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.storage.SettingsRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException

class MessageHandler(settingsRepo: SettingsRepo,
                     slackSrv: SlackSrv) {
  def handle(msg: GospeakMessage): IO[Unit] = msg match {
    case m: GospeakMessage.EventCreated => handle(m).map(_ => ())
    case m: GospeakMessage.TalkAdded => handle(m).map(_ => ())
    case m: GospeakMessage.TalkRemoved => handle(m).map(_ => ())
    case m: GospeakMessage.EventPublished => handle(m).map(_ => ())
    case m: GospeakMessage.ProposalCreated => handle(m).map(_ => ())
  }

  private def handle(msg: GospeakMessage.EventCreated): IO[Int] = handleGroupEvent(msg.group.id, Event.OnEventCreated, TemplateData.eventCreated(msg))

  private def handle(msg: GospeakMessage.TalkAdded): IO[Int] = handleGroupEvent(msg.group.id, Event.OnEventAddTalk, TemplateData.talkAdded(msg))

  private def handle(msg: GospeakMessage.TalkRemoved): IO[Int] = handleGroupEvent(msg.group.id, Event.OnEventRemoveTalk, TemplateData.talkRemoved(msg))

  private def handle(msg: GospeakMessage.EventPublished): IO[Int] = IO.pure(0)

  private def handle(msg: GospeakMessage.ProposalCreated): IO[Int] = handleGroupEvent(msg.cfp.group, Event.OnProposalCreated, TemplateData.proposalCreated(msg))

  private def handleGroupEvent(id: Group.Id, e: Event, data: TemplateData): IO[Int] = for {
    settings <- settingsRepo.find(id)
    results <- settings.events.getOrElse(e, Seq()).map(exec(settings, _, data)).sequence
  } yield results.length

  private def exec(settings: Group.Settings, action: Events.Action, data: TemplateData): IO[Unit] = action match {
    case Events.Action.Slack(slack) => settings.accounts.slack.map(slackSrv.exec(_, slack, data)).getOrElse(IO.raiseError(CustomException("No credentials for Slack")))
  }
}
