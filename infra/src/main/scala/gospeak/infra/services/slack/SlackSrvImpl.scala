package gospeak.infra.services.slack

import cats.data.EitherT
import cats.effect.IO
import gospeak.core.services.slack.SlackSrv
import gospeak.core.services.slack.domain.SlackAction.PostMessage
import gospeak.core.services.slack.domain._
import gospeak.infra.services.slack.SlackSrvImpl._
import gospeak.libs.scala.Crypto.AesSecretKey
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Markdown}
import gospeak.libs.slack.{SlackClient, domain => api}
import io.circe.Json

import scala.util.Try

// SlackSrv should not use Slack classes in the API, it's independent and the implementation should do the needed conversion
class SlackSrvImpl(client: SlackClient) extends SlackSrv {
  override def getInfos(token: SlackToken, key: AesSecretKey): IO[SlackTokenInfo] =
    toSlack(token, key).toIO.flatMap(client.info).map(_.map(toGospeak)).flatMap(toIO)

  override def exec(action: SlackAction, data: Json, creds: SlackCredentials, key: AesSecretKey): IO[Unit] = action match {
    case a: PostMessage => postMessage(a, data, creds, key).map(_ => ())
  }

  private def postMessage(action: PostMessage, data: Json, creds: SlackCredentials, key: AesSecretKey): IO[Either[api.SlackError, api.SlackMessage]] = {
    val sender = api.SlackSender.Bot(creds.name, creds.avatar.map(_.value))
    for {
      token <- toSlack(creds.token, key).toIO
      channel <- action.channel.render(data).map(toSlackName).toIO(e => CustomException(e.message))
      message <- action.message.render(data).map(toSlack).toIO(e => CustomException(e.message))
      attempt1 <- client.postMessage(token, sender, channel, message)
      attempt2 <- attempt1 match {
        case Left(api.SlackError(false, "channel_not_found", _, _)) if action.createdChannelIfNotExist =>
          (for {
            createdChannel <- EitherT(client.createChannel(token, channel))
            invitedUsers <- EitherT.liftF(if (action.inviteEverybody) {
              client.listUsers(token).flatMap(_.getOrElse(List()).map(_.id).map(client.inviteToChannel(token, createdChannel.id, _)).sequence)
            } else {
              IO.pure(List())
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

  private def toSlackName(name: String): api.SlackChannel.Name =
    api.SlackChannel.Name(name)

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
