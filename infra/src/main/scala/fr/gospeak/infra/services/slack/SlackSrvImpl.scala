package fr.gospeak.infra.services.slack

import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackAction.PostMessage
import fr.gospeak.core.services.slack.domain._
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.infra.services.slack.api.request.{SlackContent, SlackSender}
import fr.gospeak.infra.services.slack.api.{SlackChannel, SlackMessage}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException

class SlackSrvImpl(client: SlackClient,
                   templateSrv: TemplateSrv) extends SlackSrv {
  override def getInfos(token: SlackToken): IO[Either[SlackError, SlackTokenInfo]] = client.info(token)

  override def exec(creds: SlackCredentials, action: SlackAction, data: TemplateData): IO[Unit] = action match {
    case a: PostMessage => postMessage(creds, a, data).map(_ => ())
  }

  private def postMessage(creds: SlackCredentials, action: PostMessage, data: TemplateData): IO[SlackMessage] = {
    val token = SlackToken(creds.token.value)
    val sender = SlackSender.Bot(creds.name, creds.avatar.map(_.value))
    for {
      channel <- templateSrv.render(action.channel, data).map(SlackChannel.Name).toIO(CustomException(_))
      message <- templateSrv.render(action.message, data).map(SlackContent.Markdown).toIO(CustomException(_))
      res <- client.postMessage(token, sender, channel, message)
    } yield res
  }
}
