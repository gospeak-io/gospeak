package fr.gospeak.core.services.slack.domain

final case class SlackTokenInfo(url: String,
                                team: String,
                                user: String,
                                team_id: SlackTeam.Id,
                                user_id: SlackUser.Id,
                                ok: Boolean)
