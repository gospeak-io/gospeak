package fr.gospeak.infra.libs.meetup.domain

final case class MeetupEvent(id: String,
                             name: String,
                             status: String, // "cancelled", "upcoming", "past", "proposed", "suggested", or "draft"
                             visibility: String, // "public", "public_limited", or "members"
                             description: String,
                             time: Long,
                             local_date: String,
                             local_time: String,
                             utc_offset: Int,
                             duration: Option[Int],
                             venue: MeetupVenue.Basic,
                             group: MeetupGroup.Basic,
                             rsvp_limit: Option[Int],
                             yes_rsvp_count: Int,
                             waitlist_count: Int,
                             link: String,
                             member_pay_fee: Boolean,
                             created: Long,
                             updated: Long)

object MeetupEvent {

  final case class Create(name: String, // < 80 char
                          description: String, // < 50000 char, html with only <b>, <i> & <a>, img link are rendered
                          time: Long, // timestamp
                          publish_status: String = "draft", // "draft" or "published"
                          announce: Boolean = false,
                          duration: Int = 10800000, // in millis
                          venue_id: Option[Int] = None,
                          lat: Option[Double] = None,
                          lon: Option[Double] = None,
                          how_to_find_us: Option[String] = None,
                          venue_visibility: String = "public", // "public" or "members"
                          event_hosts: Option[String] = None, // coma separated member ids, <= 5
                          rsvp_limit: Option[Int] = None,
                          rsvp_close_time: Option[Long] = None, // timestamp
                          rsvp_open_time: Option[Long] = None, // timestamp
                          question: Option[String] = None, // < 250 char
                          guest_limit: Option[Int] = None, // between 0 and 2
                          featured_photo_id: Option[Int] = None,
                          self_rsvp: Boolean = true)

}
