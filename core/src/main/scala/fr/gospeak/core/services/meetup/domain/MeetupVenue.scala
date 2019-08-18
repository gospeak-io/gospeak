package fr.gospeak.core.services.meetup.domain

object MeetupVenue {

  final case class Id(value: Long) extends AnyVal

  final case class Ref(group: MeetupGroup.Slug, venue: Id)

}
