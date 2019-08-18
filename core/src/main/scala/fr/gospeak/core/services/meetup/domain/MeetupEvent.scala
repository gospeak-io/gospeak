package fr.gospeak.core.services.meetup.domain

object MeetupEvent {

  final case class Id(value: Long) extends AnyVal

  final case class Ref(group: MeetupGroup.Slug, event: Id) {
    def link: String = s"https://www.meetup.com/${group.value}/events/${event.value}"
  }

}
