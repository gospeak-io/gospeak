package gospeak.libs.meetup.domain

import java.time.{LocalDateTime, ZoneOffset}

final case class MeetupEvent(id: Long,
                             name: String,
                             status: String, // ex: "draft", "suggested", "proposed", "upcoming", "past", "cancelled"
                             visibility: String, // ex: "public", "public_limited", or "members"
                             description: Option[String],
                             time: Option[Long], // ex: 1562691600000
                             utc_offset: Option[Int], // ex: 7200000
                             local_date: Option[String], // ex: "2019-07-09"
                             local_time: Option[String], // ex: "19:00"
                             duration: Option[Int], // ex: 10800000
                             venue: Option[MeetupVenue.Basic],
                             group: MeetupGroup.Basic,
                             rsvp_limit: Option[Int],
                             yes_rsvp_count: Int,
                             waitlist_count: Int,
                             link: String,
                             member_pay_fee: Boolean,
                             created: Long,
                             updated: Long) {
  def localTime: LocalDateTime =
    time.map(MeetupEvent.toLocalDate(_, utc_offset))
      .orElse(local_date.map(MeetupEvent.toLocalDate(_, local_time)))
      .getOrElse(MeetupEvent.toLocalDate(updated, None))
}

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

  def toLocalDate(timestamp: Long, offset: Option[Int]): LocalDateTime =
    LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.ofTotalSeconds(offset.map(_ / 1000).getOrElse(0)))

  def toLocalDate(date: String, time: Option[String]): LocalDateTime =
    LocalDateTime.parse(date + "T" + time.getOrElse("00:00"))
}
