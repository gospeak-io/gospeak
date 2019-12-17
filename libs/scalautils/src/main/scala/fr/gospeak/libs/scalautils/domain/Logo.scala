package fr.gospeak.libs.scalautils.domain

final case class Logo(url: Url) extends AnyVal {
  def value: String = url.value
}
