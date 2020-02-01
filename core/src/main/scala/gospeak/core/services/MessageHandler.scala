package gospeak.core.services

import java.time.LocalDateTime

import cats.effect.IO
import gospeak.core.ApplicationConf
import gospeak.core.domain.Group
import gospeak.core.domain.Group.Settings.Action
import gospeak.core.domain.Group.Settings.Action.Trigger
import gospeak.core.domain.utils.{Constants, GsMessage, TemplateData}
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.slack.SlackSrv
import gospeak.core.services.storage.GroupSettingsRepo
import gospeak.core.services.twitter.{Tweets, TwitterSrv}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, EmailAddress}

import scala.util.control.NonFatal

class MessageHandler(appConf: ApplicationConf,
                     groupSettingsRepo: GroupSettingsRepo,
                     templateSrv: TemplateSrv,
                     markdownSrv: MarkdownSrv,
                     emailSrv: EmailSrv,
                     slackSrv: SlackSrv,
                     twitterSrv: Option[TwitterSrv]) {
  def handle(msg: GsMessage): IO[Unit] = (msg match {
    case m: GsMessage.EventCreated => handle(m)
    case m: GsMessage.EventPublished => handle(m)
    case m: GsMessage.TalkAdded => handle(m)
    case m: GsMessage.TalkRemoved => handle(m)
    case m: GsMessage.ProposalCreated => handle(m)
    case m: GsMessage.ExternalCfpCreated => handle(m)
  }).map(_ => ()).recover { case NonFatal(_) => () }

  private def handle(msg: GsMessage.EventCreated): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventCreated, TemplateData.eventCreated(msg))

  private def handle(msg: GsMessage.TalkAdded): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventAddTalk, TemplateData.talkAdded(msg))

  private def handle(msg: GsMessage.TalkRemoved): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventRemoveTalk, TemplateData.talkRemoved(msg))

  private def handle(msg: GsMessage.EventPublished): IO[Int] = handleGroupEvent(msg.group.value.id, Trigger.OnEventPublish, TemplateData.eventPublished(msg))

  private def handle(msg: GsMessage.ProposalCreated): IO[Int] = handleGroupEvent(msg.cfp.value.group, Trigger.OnProposalCreated, TemplateData.proposalCreated(msg))

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

  private def handle(msg: GsMessage.ExternalCfpCreated): IO[Int] = {
    twitterSrv
      .filter(_ => msg.cfp.value.isActive(LocalDateTime.now()))
      .map(srv => srv.tweet(Tweets.externalCfpCreated(msg.cfp.value, msg.cfp.link, msg.user))).sequence
      .map(_.map(_ => 1).getOrElse(0))
  }
}
