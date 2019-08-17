package fr.gospeak.core.services.meetup.domain

final case class MeetupCredentials(accessToken: String, // FIXME must be encoded
                                   refreshToken: String, // FIXME must be encoded
                                   group: MeetupGroup.Slug,
                                   loggedUserId: MeetupUser.Id,
                                   loggedUserName: String)

object MeetupCredentials {
  def apply(token: MeetupToken, user: MeetupUser, meetupGroup: MeetupGroup): MeetupCredentials =
    new MeetupCredentials(token.accessToken, token.refreshToken, meetupGroup.slug, user.id, user.name)
}
