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

  private def handle(msg: GospeakMessage.EventPublished): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventPublish, TemplateData.eventPublished(msg))

  private def handle(msg: GospeakMessage.ProposalCreated): IO[Int] = handleGroupEvent(msg.cfp.value.group, Trigger.OnProposalCreated, TemplateData.proposalCreated(msg))

  private def handleGroupEvent(id: Group.Id, trigger: Trigger, data: TemplateData): IO[Int] = for {
    actions <- groupSettingsRepo.findActions(id)
    accounts <- groupSettingsRepo.findAccounts(id)
    results <- actions.getOrElse(trigger, Seq()).map(exec(accounts, _, data)).sequence
  } yield results.length

  private def exec(accounts: Group.Settings.Accounts, action: Action, data: TemplateData): IO[Unit] = action match {
    case Action.Slack(slack) => accounts.slack.map(slackSrv.exec(slack, data, _, appConf.aesKey)).getOrElse(IO.raiseError(CustomException("No credentials for Slack")))
  }
}
