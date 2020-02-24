package gospeak.libs.meetup.domain

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

  final case class Create(name: String,
                          address_1: String,
                          city: String,
                          state: Option[String],
                          country: String,
                          localized_country_name: String,
                          lat: Double,
                          lon: Double,
                          repinned: Boolean,
                          visibility: String) {
    def toMap: Map[String, String] = Map(
      "name" -> Some(name),
      "address_1" -> Some(address_1),
      "city" -> Some(city),
      "state" -> state,
      "country" -> Some(country),
      "localized_country_name" -> Some(localized_country_name),
      "lat" -> Some(lat.toString),
      "lon" -> Some(lon.toString),
      "repinned" -> Some(repinned.toString),
      "visibility" -> Some(visibility)).collect { case (k, Some(v)) => (k, v) }
  }

}
