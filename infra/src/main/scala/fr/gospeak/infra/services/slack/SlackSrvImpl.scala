package fr.gospeak.infra.services.slack

import cats.data.EitherT
import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.TemplateSrv
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackAction.PostMessage
import fr.gospeak.core.services.slack.domain._
import fr.gospeak.infra.libs.slack.{SlackClient, domain => api}
import fr.gospeak.infra.services.slack.SlackSrvImpl._
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Markdown}

import scala.util.Try

// SlackSrv should not use Slack classes in the API, it's independent and the implementation should do the needed conversion
class SlackSrvImpl(client: SlackClient,
                   templateSrv: TemplateSrv) extends SlackSrv {
  override def getInfos(token: SlackToken, key: AesSecretKey): IO[SlackTokenInfo] =
    toSlack(token, key).toIO.flatMap(client.info).map(_.map(toGospeak)).flatMap(toIO)

  override def exec(action: SlackAction, data: TemplateData, creds: SlackCredentials, key: AesSecretKey): IO[Unit] = action match {
    case a: PostMessage => postMessage(a, data, creds, key).map(_ => ())
  }

  private def postMessage(action: PostMessage, data: TemplateData, creds: SlackCredentials, key: AesSecretKey): IO[Either[api.SlackError, api.SlackMessage]] = {
    val sender = api.SlackSender.Bot(creds.name, creds.avatar.map(_.value))
    for {
      token <- toSlack(creds.token, key).toIO
      channel <- templateSrv.render(action.channel, data).map(toSlackName).toIO(CustomException(_))
      message <- templateSrv.render(action.message, data).map(toSlack).toIO(CustomException(_))
      attempt1 <- client.postMessage(token, sender, channel, message)
      attempt2 <- attempt1 match {
        case Left(api.SlackError(false, "channel_not_found", _, _)) if action.createdChannelIfNotExist =>
          (for {
            createdChannel <- EitherT(client.createChannel(token, channel))
            invitedUsers <- EitherT.liftF(if (action.inviteEverybody) {
              client.listUsers(token).flatMap(_.getOrElse(Seq()).map(_.id).map(client.inviteToChannel(token, createdChannel.id, _)).sequence)
            } else {
              IO.pure(Seq())
            })
            attempt2 <- EitherT(client.postMessage(token, sender, createdChannel.id, message))
          } yield attempt2).value
        case _ => IO.pure(attempt1)
      }
    } yield attempt2
  }

  private def toSlack(token: SlackToken, key: AesSecretKey): Try[api.SlackToken] =
    token.decode(key).map(api.SlackToken)

  private def toSlack(md: Markdown): api.SlackContent.Markdown =
    api.SlackContent.Markdown(md.value)

  private def toSlackName(md: Markdown): api.SlackChannel.Name =
    api.SlackChannel.Name(md.value)

  private def toGospeak(id: api.SlackUser.Id): SlackUser.Id =
    SlackUser.Id(id.value)

  private def toGospeak(info: api.SlackTokenInfo): SlackTokenInfo =
    SlackTokenInfo(SlackTeam.Id(info.team_id.value), info.team, info.url, toGospeak(info.user_id), info.user)

  private def toIO[A](e: Either[api.SlackError, A]): IO[A] =
    e.toIO(e => CustomException(format(e)))
}

object SlackSrvImpl {
  private[slack] def format(err: api.SlackError): String =
    err.error +
      err.needed.map(" " + _).getOrElse("") +
      err.provided.map(" (provided: " + _ + ")").getOrElse("")
}
