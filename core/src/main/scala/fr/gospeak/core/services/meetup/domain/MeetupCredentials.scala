package fr.gospeak.core.services.meetup.domain

final case class MeetupCredentials(accessToken: String, // FIXME must be encoded
                                   refreshToken: String, // FIXME must be encoded
                                   loggedUserId: MeetupUser.Id,
                                   loggedUserName: String)

object MeetupCredentials {
  def apply(token: MeetupToken, user: MeetupUser): MeetupCredentials =
    new MeetupCredentials(token.accessToken, token.refreshToken, user.id, user.name)
}
