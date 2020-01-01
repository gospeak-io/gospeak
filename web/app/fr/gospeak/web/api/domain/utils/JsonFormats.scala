package fr.gospeak.web.api.domain.utils

import play.api.libs.json.{JsNumber, Writes}

import scala.concurrent.duration.FiniteDuration

object JsonFormats {
  implicit val writesFiniteDuration: Writes[FiniteDuration] = (o: FiniteDuration) => JsNumber(o.toMinutes)
}
