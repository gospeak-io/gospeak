package fr.gospeak.infra.libs.timeshape

import java.time.ZoneId

import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Geo
import net.iakovlev.timeshape.TimeZoneEngine

import scala.util.Try

class TimeShape(engine: TimeZoneEngine) {
  def getZoneId(geo: Geo): Option[ZoneId] = engine.query(geo.lat, geo.lng).asScala
}

object TimeShape {
  def create(): Try[TimeShape] = Try(TimeZoneEngine.initialize()).map(new TimeShape(_))
}
