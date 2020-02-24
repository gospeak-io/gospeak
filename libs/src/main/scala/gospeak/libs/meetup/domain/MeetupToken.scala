package gospeak.libs.meetup.domain

final case class MeetupToken(access_token: String,
                             token_type: String,
                             expires_in: Int,
                             refresh_token: String)

object MeetupToken {

  final case class Access(value: String) extends AnyVal

}
