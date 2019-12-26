package fr.gospeak.core.services

import cats.effect.IO
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.Group
import fr.gospeak.core.domain.Group.Settings.Action
import fr.gospeak.core.domain.Group.Settings.Action.Trigger
import fr.gospeak.core.domain.utils.{Constants, GospeakMessage, TemplateData}
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.storage.GroupSettingsRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, EmailAddress}

import scala.util.control.NonFatal

class MessageHandler(appConf: ApplicationConf,
                     groupSettingsRepo: GroupSettingsRepo,
                     templateSrv: TemplateSrv,
                     markdownSrv: MarkdownSrv,
                     emailSrv: EmailSrv,
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
    case email: Action.Email =>
      (for {
        to <- templateSrv.render(email.to, data).leftMap(CustomException(_)).flatMap(EmailAddress.from).map(EmailAddress.Contact(_))
        subject <- templateSrv.render(email.subject, data).leftMap(CustomException(_))
        content <- templateSrv.render(email.content, data).map(markdownSrv.render(_)).leftMap(CustomException(_))
      } yield emailSrv.send(EmailSrv.Email(
        from = Constants.Contact.noReply,
        to = Seq(to),
        subject = subject,
        content = EmailSrv.HtmlContent(content.value)
      ))).toIO.flatMap(identity).map(_ => ())
    case Action.Slack(slack) => accounts.slack.map(slackSrv.exec(slack, data, _, appConf.aesKey)).getOrElse(IO.raiseError(CustomException("No credentials for Slack")))
  }
}
