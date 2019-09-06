package fr.gospeak.infra.libs.meetup.domain

final case class MeetupEvent(id: String,
                             name: String,
                             status: String, // "cancelled", "upcoming", "past", "proposed", "suggested", or "draft"
                             visibility: String, // "public", "public_limited", or "members"
                             description: String,
                             time: Option[Long],
                             local_date: Option[String],
                             local_time: Option[String],
                             utc_offset: Option[Int],
                             duration: Option[Int],
                             venue: Option[MeetupVenue.Basic],
                             group: MeetupGroup.Basic,
                             rsvp_limit: Option[Int],
                             yes_rsvp_count: Int,
                             waitlist_count: Int,
                             link: String,
                             member_pay_fee: Boolean,
                             created: Long,
                             updated: Long)

object MeetupEvent {
  val maxHosts = 5

  final case class Create(name: String, // < 80 char
                          description: String, // < 50000 char, html with only <b>, <i> & <a>, img link are rendered
                          time: Long, // timestamp
                          publish_status: String = "draft", // "draft" or "published"
                          announce: Boolean = false,
                          duration: Int = 10800000, // in millis
                          venue_visibility: String = "public", // "public" or "members"
                          venue_id: Option[Long] = None,
                          lat: Option[Double] = None,
                          lon: Option[Double] = None,
                          how_to_find_us: Option[String] = None,
                          rsvp_limit: Option[Int] = None,
                          rsvp_close_time: Option[Long] = None, // timestamp
                          rsvp_open_time: Option[Long] = None, // timestamp
                          event_hosts: Option[String] = None, // coma separated member ids, <= 5
                          question: Option[String] = None, // < 250 char
                          guest_limit: Option[Int] = None, // between 0 and 2
                          featured_photo_id: Option[Int] = None,
                          self_rsvp: Boolean = true) {
    def toMap: Map[String, String] = Map(
      "name" -> Some(name),
      "description" -> Some(description),
      "time" -> Some(time.toString),
      "publish_status" -> Some(publish_status),
      "announce" -> Some(announce.toString),
      "duration" -> Some(duration.toString),
      "venue_visibility" -> Some(venue_visibility),
      "venue_id" -> venue_id.map(_.toString),
      "lat" -> lat.map(_.toString),
      "lon" -> lon.map(_.toString),
      "how_to_find_us" -> how_to_find_us,
      "rsvp_limit" -> rsvp_limit.map(_.toString),
      "rsvp_close_time" -> rsvp_close_time.map(_.toString),
      "rsvp_open_time" -> rsvp_open_time.map(_.toString),
      "event_hosts" -> event_hosts,
      "question" -> question,
      "guest_limit" -> guest_limit.map(_.toString),
      "featured_photo_id" -> featured_photo_id.map(_.toString),
      "self_rsvp" -> Some(self_rsvp.toString)).collect { case (k, Some(v)) => (k, v) }
  }

}
