package fr.gospeak.libs.scalautils.domain

import java.time.{ZoneId, ZoneOffset}

case class GMapPlace(id: String,
                     name: String,
                     streetNo: Option[String],
                     street: Option[String],
                     postalCode: Option[String],
                     locality: Option[String],
                     country: String,
                     formatted: String,
                     input: String,
                     lat: Double,
                     lng: Double,
                     url: String,
                     website: Option[String],
                     phone: Option[String],
                     utcOffset: Int, // in minutes
                     timezone: ZoneId) {
  def value: String = formatted

  def zoneOffset: ZoneOffset = ZoneOffset.ofTotalSeconds(utcOffset * 60)

  def trim: GMapPlace = GMapPlace(
    id = id.trim,
    name = name.trim,
    streetNo = streetNo.map(_.trim).filter(_.nonEmpty),
    street = street.map(_.trim).filter(_.nonEmpty),
    postalCode = postalCode.map(_.trim).filter(_.nonEmpty),
    locality = locality.map(_.trim).filter(_.nonEmpty),
    country = country.trim,
    formatted = formatted.trim,
    input = input.trim,
    lat = lat,
    lng = lng,
    url = url.trim,
    website = website.map(_.trim).filter(_.nonEmpty),
    phone = phone.map(_.trim).filter(_.nonEmpty),
    utcOffset = utcOffset,
    timezone = timezone)
}
