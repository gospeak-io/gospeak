package fr.gospeak.infra.libs.meetup.domain

final case class MeetupVenue(id: Long,
                             name: String,
                             address_1: String,
                             city: String,
                             state: Option[String],
                             country: String,
                             localized_country_name: String,
                             lat: Double,
                             lon: Double,
                             visibility: String,
                             rating: Double,
                             rating_count: Int)

object MeetupVenue {

  final case class Basic(id: Long,
                         name: String,
                         address_1: String,
                         city: String,
                         country: String,
                         localized_country_name: String,
                         lat: Double,
                         lon: Double,
                         repinned: Boolean)

}
