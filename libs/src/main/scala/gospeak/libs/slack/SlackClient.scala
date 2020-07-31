package gospeak.libs.slack

import cats.effect.IO
import gospeak.libs.slack.SlackJson._
import gospeak.libs.slack.domain._
import gospeak.libs.http.HttpClient
import gospeak.libs.http.HttpClient.Response
import io.circe.Decoder
import io.circe.parser._

// SlackClient should not depend on core module, it's an independent lib
// scala lib: https://github.com/slack-scala-client/slack-scala-client
class SlackClient(http: HttpClient) {
  private val baseUrl = "https://slack.com/api"

  // cf https://api.slack.com/methods/auth.test
  def info(token: SlackToken): IO[Either[SlackError, SlackTokenInfo]] =
    http.get(s"$baseUrl/auth.test", query = Map("token" -> token.value))
      .flatMap(parse[SlackTokenInfo])

  // cf https://api.slack.com/methods/channels.create
  def createChannel(token: SlackToken, name: SlackChannel.Name): IO[Either[SlackError, SlackChannel]] =
    http.get(s"$baseUrl/channels.create", query = Map(
      "token" -> token.value,
      "name" -> name.value
    )).flatMap(parse[SlackChannel.Single]).map(_.map(_.channel))

  // cf https://api.slack.com/methods/channels.invite
  def inviteToChannel(token: SlackToken, channel: SlackChannel.Id, user: SlackUser.Id): IO[Either[SlackError, SlackChannel]] =
    http.get(s"$baseUrl/channels.invite", query = Map(
      "token" -> token.value,
      "channel" -> channel.value,
      "user" -> user.value
    )).flatMap(parse[SlackChannel.Single]).map(_.map(_.channel))

  // cf https://api.slack.com/methods/channels.list
  def listChannels(token: SlackToken): IO[Either[SlackError, Seq[SlackChannel]]] =
    http.get(s"$baseUrl/channels.list", query = Map("token" -> token.value))
      .flatMap(parse[SlackChannel.List]).map(_.map(_.channels))

  // cf https://api.slack.com/methods/users.list
  def listUsers(token: SlackToken): IO[Either[SlackError, Seq[SlackUser]]] =
    http.get(s"$baseUrl/users.list", query = Map("token" -> token.value))
      .flatMap(parse[SlackUser.List]).map(_.map(_.members))

  // cf https://api.slack.com/methods/chat.postMessage
  def postMessage(token: SlackToken, sender: SlackSender, channel: SlackChannel.Ref, msg: SlackContent): IO[Either[SlackError, SlackMessage]] =
    http.get(s"$baseUrl/chat.postMessage", query = sender.toOpts ++ msg.toOpts ++ Map(
      "token" -> token.value,
      "channel" -> channel.value
    )).flatMap(parse[SlackMessage.Posted]).map(_.map(_.message))

  private def parse[A](res: Response)(implicit d: Decoder[A]): IO[Either[SlackError, A]] = {
    decode[A](res.body) match {
      case Right(info) => IO.pure(Right(info))
      case Left(err) => decode[SlackError](res.body) match {
        case Right(fail) => IO.pure(Left(fail))
        case Left(_) => IO.raiseError(err)
      }
    }
  }
}
