package fr.gospeak.infra.services.slack

import cats.effect.IO
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.slack.domain.SlackAction.PostMessage
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackCredentials, SlackError, SlackToken, SlackTokenInfo}
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.infra.services.slack.api.request.{SlackContent, SlackSender}
import fr.gospeak.infra.services.slack.api.{SlackChannel, SlackMessage}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.CustomException
import io.circe._
import io.circe.generic.extras._
import io.circe.generic.extras.semiauto._

class SlackSrvImpl(client: SlackClient,
                   templateSrv: TemplateSrv) extends SlackSrv {
  private implicit val circeConfiguration: Configuration = Configuration.default.withDiscriminator("type")
  private implicit val templateDataCfpEncoder: Encoder[TemplateData.Cfp] = deriveEncoder[TemplateData.Cfp]
  private implicit val templateDataProposalEncoder: Encoder[TemplateData.Proposal] = deriveEncoder[TemplateData.Proposal]
  private implicit val templateDataUserEncoder: Encoder[TemplateData.User] = deriveEncoder[TemplateData.User]
  private implicit val templateDataProposalCreatedEncoder: Encoder[TemplateData.ProposalCreated] = deriveEncoder[TemplateData.ProposalCreated]
  private implicit val templateDataEncoder: Encoder[TemplateData] = deriveEncoder[TemplateData]

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
