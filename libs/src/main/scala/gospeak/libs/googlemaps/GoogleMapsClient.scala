package gospeak.libs.googlemaps

import cats.effect.IO
import com.google.maps.model.{AddressComponent, LatLng, PlaceDetails, PlacesSearchResult}
import com.google.maps.{GeoApiContext, PendingResult, PlacesApi}
import gospeak.libs.googlemaps.GoogleMapsClient._
import gospeak.libs.scala.domain.{GMapPlace, Geo}

class GoogleMapsClient private(ctx: GeoApiContext) extends AutoCloseable {
  def search(query: String, geo: Geo): IO[List[GMapPlace]] =
    exec(PlacesApi.textSearchQuery(ctx, query)
      .location(as(geo))
      .radius(50000)).map(_.results.toList.map(as(query, _)))

  def getPlace(id: String): IO[GMapPlace] =
    exec(PlacesApi.placeDetails(ctx, id)).map(as)

  override def close(): Unit = ctx.shutdown()

  private def exec[A](r: PendingResult[A]): IO[A] = {
    IO.async { cb =>
      r.setCallback(new PendingResult.Callback[A] {
        override def onResult(result: A): Unit = cb(Right(result))

        override def onFailure(e: Throwable): Unit = cb(Left(e))
      })
    }
  }
}

object GoogleMapsClient {
  def create(apiKey: String): GoogleMapsClient = {
    val ctx = new GeoApiContext.Builder().apiKey(apiKey).build()
    new GoogleMapsClient(ctx)
  }

  private def as(query: String, p: PlacesSearchResult): GMapPlace =
    GMapPlace(
      id = p.placeId,
      name = p.name,
      streetNo = None,
      street = None,
      postalCode = None,
      locality = None,
      country = "",
      formatted = p.formattedAddress,
      input = query,
      geo = as(p.geometry.location),
      url = "",
      website = None,
      phone = None,
      utcOffset = 0)

  private def as(p: PlaceDetails): GMapPlace = {
    def find(a: Array[AddressComponent], kind: String): Option[String] = a.find(_.types.exists(_.toString == kind)).map(_.longName)

    GMapPlace(
      id = p.placeId,
      name = p.name,
      streetNo = find(p.addressComponents, "street_number"),
      street = find(p.addressComponents, "route"),
      postalCode = find(p.addressComponents, "postal_code"),
      locality = find(p.addressComponents, "locality"),
      country = find(p.addressComponents, "country").getOrElse(""),
      formatted = p.formattedAddress,
      input = "",
      geo = as(p.geometry.location),
      url = p.url.toString,
      website = Option(p.website).map(_.toString),
      phone = Option(p.formattedPhoneNumber),
      utcOffset = p.utcOffset)
  }

  def as(latlng: LatLng): Geo = Geo(latlng.lat, latlng.lng)

  def as(geo: Geo): LatLng = new LatLng(geo.lat, geo.lng)
}
