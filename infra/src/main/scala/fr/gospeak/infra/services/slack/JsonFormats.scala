package fr.gospeak.infra.services.slack

import java.time.Instant

import cats.syntax.either._
import fr.gospeak.infra.services.slack.api.{SlackChannel, SlackMessage, SlackUser}
import fr.gospeak.infra.utils.CirceUtils._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object JsonFormats {
  private val _: Decoder[SlackChannel.Id] = decodeSingleValueClass // to keep fr.gospeak.infra.utils.CirceUtils._ import
  private implicit val instantDecoder: Decoder[Instant] = Decoder.decodeLong.emap { timestampSecs =>
    Either.catchNonFatal(Instant.ofEpochSecond(timestampSecs)).leftMap(e => s"Bad Instant: ${e.getMessage}")
  }

  private implicit val slackChannelPurposeDecoder: Decoder[SlackChannel.Purpose] = deriveDecoder[SlackChannel.Purpose]
  private implicit val slackChannelTopicDecoder: Decoder[SlackChannel.Topic] = deriveDecoder[SlackChannel.Topic]
  private implicit val slackChannelDecoder: Decoder[SlackChannel] = deriveDecoder[SlackChannel]
  implicit val slackChannelSingleDecoder: Decoder[SlackChannel.Single] = deriveDecoder[SlackChannel.Single]
  implicit val slackChannelListDecoder: Decoder[SlackChannel.List] = deriveDecoder[SlackChannel.List]

  private implicit val slackUserProfileDecoder: Decoder[SlackUser.Profile] = deriveDecoder[SlackUser.Profile]
  private implicit val slackUserDecoder: Decoder[SlackUser] = deriveDecoder[SlackUser]
  implicit val slackUserListDecoder: Decoder[SlackUser.List] = deriveDecoder[SlackUser.List]

  private implicit val slackMessageDecoder: Decoder[SlackMessage] = deriveDecoder[SlackMessage]
  implicit val slackMessagePostedDecoder: Decoder[SlackMessage.Posted] = deriveDecoder[SlackMessage.Posted]
}
