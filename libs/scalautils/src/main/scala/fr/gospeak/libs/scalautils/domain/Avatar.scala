package fr.gospeak.libs.scalautils.domain

final case class Avatar(url: Url) {
  def value: String = url.value
}
