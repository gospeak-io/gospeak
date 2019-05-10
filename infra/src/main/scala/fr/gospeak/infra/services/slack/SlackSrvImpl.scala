package fr.gospeak.infra.services.slack

import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackAction.PostMessage
import fr.gospeak.core.services.slack.domain._
import fr.gospeak.infra.libs.slack.domain.SlackMessage
import fr.gospeak.infra.libs.slack.{SlackClient, domain => api}
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException

// SlackSrv should not use Slack classes in the API, it's independent and the implementation should do the needed conversion
class SlackSrvImpl(client: SlackClient,
                   templateSrv: TemplateSrv) extends SlackSrv {
  override def getInfos(token: SlackToken): IO[Either[SlackError, SlackTokenInfo]] =
    client.info(toSlack(token)).map(toGospeak)

  override def exec(creds: SlackCredentials, action: SlackAction, data: TemplateData): IO[Unit] = action match {
    case a: PostMessage => postMessage(creds, a, data).map(_ => ())
  }

  private def postMessage(creds: SlackCredentials, action: PostMessage, data: TemplateData): IO[SlackMessage] = {
    val token = toSlack(creds.token)
    val sender = api.SlackSender.Bot(creds.name, creds.avatar.map(_.value))
    for {
      channel <- templateSrv.render(action.channel, data).map(api.SlackChannel.Name).toIO(CustomException(_))
      message <- templateSrv.render(action.message, data).map(api.SlackContent.Markdown).toIO(CustomException(_))
      res <- client.postMessage(token, sender, channel, message)
    } yield res
  }

  private def toSlack(token: SlackToken): api.SlackToken =
    api.SlackToken(token.value)

  private def toGospeak(err: api.SlackError): SlackError =
    SlackError(err.error)

  private def toGospeak(id: api.SlackUser.Id): SlackUser.Id =
    SlackUser.Id(id.value)

  private def toGospeak(info: api.SlackTokenInfo): SlackTokenInfo =
    SlackTokenInfo(SlackTeam.Id(info.team_id.value), info.team, info.url, toGospeak(info.user_id), info.user)

  private def toGospeak(e: Either[api.SlackError, api.SlackTokenInfo]): Either[SlackError, SlackTokenInfo] =
    e.map(toGospeak).leftMap(toGospeak)
}
