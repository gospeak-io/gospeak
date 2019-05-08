package fr.gospeak.core.services

import cats.effect.IO
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.Group.Settings.Events
import fr.gospeak.core.domain.Group.Settings.Events.Event.OnProposalCreated
import fr.gospeak.core.domain.utils.{GospeakMessage, TemplateData}
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.storage.SettingsRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException

class MessageHandler(settingsRepo: SettingsRepo,
                     slackSrv: SlackSrv) {
  def handle(msg: GospeakMessage): IO[Unit] = msg match {
    case m: GospeakMessage.ProposalCreated => handleCreateProposal(m).map(_ => ())
    case _ => IO.pure(())
  }

  private def handleCreateProposal(msg: GospeakMessage.ProposalCreated): IO[Int] = for {
    settings <- settingsRepo.find(msg.cfp.group)
    results <- settings.events.getOrElse(OnProposalCreated, Seq()).map(exec(settings, _, msg)).sequence
  } yield results.length

  private def exec(settings: Group.Settings, action: Events.Action, msg: GospeakMessage.ProposalCreated): IO[Unit] = action match {
    case Events.Action.Slack(slack) =>
      settings.accounts.slack.map(slackSrv.exec(_, slack, TemplateData.ProposalCreated(msg))).getOrElse(IO.raiseError(CustomException("No credentials for Slack")))
  }
}
