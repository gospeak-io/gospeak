package fr.gospeak.web.utils

import fr.gospeak.core.services.slack.domain.{SlackTeam, SlackTokenInfo, SlackUser}
import fr.gospeak.web.api.ui.SuggestedItem
import play.api.libs.json.{Json, Writes}

object JsonFormats {
  implicit val suggestedItemWrites: Writes[SuggestedItem] = Json.writes[SuggestedItem]
  implicit val slackTeamIdWrites: Writes[SlackTeam.Id] = Json.writes[SlackTeam.Id]
  implicit val slackUserIdWrites: Writes[SlackUser.Id] = Json.writes[SlackUser.Id]
  implicit val slackTokenInfoWrites: Writes[SlackTokenInfo] = Json.writes[SlackTokenInfo]
}
