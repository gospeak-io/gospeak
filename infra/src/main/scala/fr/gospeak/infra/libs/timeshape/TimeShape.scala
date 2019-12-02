package fr.gospeak.infra.libs.timeshape

import java.time.ZoneId

import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.libs.scalautils.domain.Geo

import scala.util.Success
// import net.iakovlev.timeshape.TimeZoneEngine

import scala.util.Try

trait TimeShape {
  def getZoneId(geo: Geo): Option[ZoneId]
}

object TimeShape {
  // def create(): Try[TimeShape] = Try(TimeZoneEngine.initialize()).map(new RealTimeShape(_))
  def create(): Try[TimeShape] = Success(new FakeTimeShape)
}

class FakeTimeShape extends TimeShape {
  def getZoneId(geo: Geo): Option[ZoneId] = Some(Constants.defaultZoneId)
}

/* class RealTimeShape(engine: TimeZoneEngine) extends TimeShape {
  def getZoneId(geo: Geo): Option[ZoneId] = engine.query(geo.lat, geo.lng).asScala
} */
