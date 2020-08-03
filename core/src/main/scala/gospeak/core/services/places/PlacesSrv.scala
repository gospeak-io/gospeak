package gospeak.core.services.places

import cats.effect.IO
import gospeak.libs.scala.domain.{GMapPlace, Geo}

trait PlacesSrv extends AutoCloseable {
  def find(query: String, location: Geo): IO[Option[GMapPlace]]
}
