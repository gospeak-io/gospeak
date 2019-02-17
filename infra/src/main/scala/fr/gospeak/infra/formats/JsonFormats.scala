package fr.gospeak.infra.formats

import fr.gospeak.libs.scalautils.domain.GMapPlace
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._

import scala.util.Try

object JsonFormats {
  implicit val gMapPlaceDecoder: Decoder[GMapPlace] = deriveDecoder[GMapPlace]
  implicit val gMapPlaceEncoder: Encoder[GMapPlace] = deriveEncoder[GMapPlace]

  def toJson[A](value: A)(implicit e: Encoder[A]): String = value.asJson.toString()

  def fromJson[A](json: String)(implicit d: Decoder[A]): Try[A] = parse(json).map(_.as[A]).flatMap(identity).toTry
}
