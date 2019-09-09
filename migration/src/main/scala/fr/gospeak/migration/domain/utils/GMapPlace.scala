package fr.gospeak.migration.domain.utils

import java.time.ZoneId

import fr.gospeak.libs.scalautils.domain.{GMapPlace => NewGMapPlace}

case class GMapPlace(id: String,
                     name: String,
                     streetNo: Option[String],
                     street: Option[String],
                     postalCode: Option[String],
                     locality: Option[String],
                     country: String,
                     formatted: String,
                     input: String,
                     coords: Coords,
                     url: String,
                     website: Option[String],
                     phone: Option[String]) {
  def toGMapPlace: NewGMapPlace = {
    NewGMapPlace(
      id = id,
      name = name,
      streetNo = streetNo,
      street = street,
      postalCode = postalCode,
      locality = locality,
      country = country,
      formatted = formatted,
      input = input,
      lat = coords.lat,
      lng = coords.lng,
      url = url,
      website = website,
      phone = phone,
      utcOffset = 120, // France offset
      timezone = ZoneId.of("Europe/Paris"))
  }
}

case class Coords(lat: Double,
                  lng: Double)
