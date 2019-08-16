package fr.gospeak.core.services.meetup.domain

final case class MeetupException(method: String,
                                 error: String) extends Exception(s"Meetup API error when $method: $error")
