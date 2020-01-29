package gospeak.core.services.slack.domain

final case class SlackTokenInfo(teamId: SlackTeam.Id,
                                teamName: String,
                                teamUrl: String,
                                userId: SlackUser.Id,
                                userName: String)
