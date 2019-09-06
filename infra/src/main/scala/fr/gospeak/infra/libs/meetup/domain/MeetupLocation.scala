package fr.gospeak.infra.libs.meetup.domain

final case class MeetupLocation(name_string: String,
                                city: String,
                                country: String,
                                localized_country_name: String,
                                zip: String,
                                lat: Double,
                                lon: Double)
