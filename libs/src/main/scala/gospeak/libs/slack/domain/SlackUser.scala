package gospeak.libs.slack.domain

import java.time.Instant

// cf https://api.slack.com/types/user
final case class SlackUser(id: SlackUser.Id,
                           team_id: SlackTeam.Id,
                           name: String,
                           deleted: Boolean,
                           color: Option[String],
                           real_name: Option[String],
                           profile: SlackUser.Profile,
                           status: Option[String],
                           is_bot: Boolean,
                           is_app_user: Boolean,
                           is_admin: Option[Boolean],
                           is_owner: Option[Boolean],
                           is_stranger: Option[Boolean],
                           has_2fa: Option[Boolean],
                           tz: Option[String],
                           tz_label: Option[String],
                           tz_offset: Option[Int],
                           locale: Option[String],
                           updated: Instant)

object SlackUser {

  final case class Id(value: String) extends AnyVal

  final case class Profile(team: SlackTeam.Id,
                           real_name: String,
                           real_name_normalized: String,
                           first_name: Option[String],
                           last_name: Option[String],
                           email: Option[String],
                           phone: Option[String],
                           skype: Option[String],
                           status_text: String,
                           status_emoji: String,
                           avatar_hash: String,
                           image_24: String,
                           image_32: String,
                           image_48: String,
                           image_72: String,
                           image_192: String)

  final case class List(members: Seq[SlackUser],
                        ok: Boolean)

}
