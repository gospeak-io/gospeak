package gospeak.infra.services.places

import cats.effect.IO
import gospeak.core.services.places.{GoogleMapsConf, PlacesSrv}
import gospeak.libs.googlemaps.GoogleMapsClient
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{GMapPlace, Geo}

class PlacesSrvImpl(client: GoogleMapsClient) extends PlacesSrv {
  override def find(query: String, location: Geo): IO[Option[GMapPlace]] = for {
    results <- client.search(query, location)
    place <- results.headOption.map(r => client.getPlace(r.id)).sequence
  } yield place.map(_.copy(input = query))

  override def close(): Unit = client.close()
}

object PlacesSrvImpl {
  def from(conf: GoogleMapsConf): PlacesSrvImpl =
    new PlacesSrvImpl(GoogleMapsClient.create(conf.backendApiKey.decode))
}
