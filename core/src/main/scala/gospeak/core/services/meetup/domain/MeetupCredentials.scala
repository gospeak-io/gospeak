package gospeak.core.services.meetup.domain

import gospeak.libs.scala.domain.Crypted

final case class MeetupCredentials(accessToken: Crypted,
                                   refreshToken: Crypted,
                                   group: MeetupGroup.Slug,
                                   loggedUserId: MeetupUser.Id,
                                   loggedUserName: String)

object MeetupCredentials {
  def apply(token: MeetupToken, user: MeetupUser, meetupGroup: MeetupGroup): MeetupCredentials =
    new MeetupCredentials(token.accessToken, token.refreshToken, meetupGroup.slug, user.id, user.name)
}
