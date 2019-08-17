package fr.gospeak.core.services.meetup.domain

sealed abstract class MeetupException(msg: String) extends Exception(msg)

object MeetupException {

  final case class RequestToken(error: String) extends MeetupException(s"Meetup API error while requesting access_token: $error")

  final case class LoggedUser(error: String) extends MeetupException(s"Meetup API error while fetching logged user: $error")

  final case class GroupNotFound(slug: MeetupGroup.Slug,
                                 error: String) extends MeetupException(s"Meetup group '${slug.value}' not found: $error")

}
