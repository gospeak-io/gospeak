package gospeak.web.services

import java.time.LocalDateTime

import cats.data.OptionT
import cats.effect.IO
import gospeak.core.ApplicationConf
import gospeak.core.domain.Group
import gospeak.core.domain.Group.Settings.Action
import gospeak.core.domain.Group.Settings.Action.Trigger
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils.Constants
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.slack.SlackSrv
import gospeak.core.services.storage.{OrgaGroupRepo, OrgaGroupSettingsRepo}
import gospeak.core.services.twitter.{Tweets, TwitterSrv}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, EmailAddress}
import gospeak.web.services.MessageSrv._
import io.circe.Json
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

class MessageHandler(appConf: ApplicationConf,
                     groupRepo: OrgaGroupRepo,
                     groupSettingsRepo: OrgaGroupSettingsRepo,
                     emailSrv: EmailSrv,
                     slackSrv: SlackSrv,
                     twitterSrv: Option[TwitterSrv]) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def groupActionHandler(msg: Message): IO[Unit] = (msg match {
    case m: Message.GroupMessage => handleGroupAction(m.group.slug, m, eMessage(m))
    case _ => IO.pure(0)
  }).map(_ => ()).recover { case NonFatal(_) => () }

  def gospeakHandler(msg: Message): IO[Unit] = (msg match {
    case m: Message.ExternalCfpCreated => gospeakTwitt(m)
    case _ => IO.pure(0)
  }).map(_ => ()).recover { case NonFatal(_) => () }

  def logHandler(msg: Message): IO[Unit] = IO.pure(logger.info(s"Message sent: $msg"))

  private def handleGroupAction(group: Group.Slug, msg: Message.GroupMessage, data: Json): IO[Int] = (for {
    groupElt <- OptionT(groupRepo.find(group))
    actions <- OptionT.liftF(groupSettingsRepo.findActions(groupElt.id))
    accounts <- OptionT.liftF(groupSettingsRepo.findAccounts(groupElt.id))
    actionsToExec = Trigger.all.filter(_.message == msg.ref).flatMap(actions.getOrElse(_, Seq()))
    results <- OptionT.liftF(actionsToExec.map(execGroupAction(accounts, _, data)).sequence)
  } yield results.length).value.map(_.getOrElse(0))

  private def execGroupAction(accounts: Group.Settings.Accounts, action: Action, data: Json): IO[Unit] = action match {
    case email: Action.Email =>
      (for {
        to <- email.to.render(data).left.map(e => CustomException(e.message)).flatMap(EmailAddress.from).map(EmailAddress.Contact(_))
        subject <- email.subject.render(data).left.map(e => CustomException(e.message))
        content <- email.content.render(data).map(_.toHtml).leftMap(e => CustomException(e.message))
      } yield emailSrv.send(EmailSrv.Email(
        from = Constants.Gospeak.noreplyEmail,
        to = Seq(to),
        subject = subject,
        content = EmailSrv.HtmlContent(content.value)
      ))).toIO.flatMap(identity).map(_ => ())
    case Action.Slack(slack) =>
      accounts.slack.map(slackSrv.exec(slack, data, _, appConf.aesKey)).getOrElse(IO.raiseError(CustomException("No credentials for Slack")))
  }

  private def gospeakTwitt(msg: Message.ExternalCfpCreated): IO[Int] = {
    twitterSrv
      .filter(_ => msg.cfp.isActive(LocalDateTime.now()))
      .map(srv => srv.tweet(Tweets.externalCfpCreated(msg))).sequence
      .map(_.map(_ => 1).getOrElse(0))
  }
}
