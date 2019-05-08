package fr.gospeak.libs.scalautils.domain

sealed trait Template {
  val value: String
}

object Template {

  final case class Mustache(value: String) extends Template

}
