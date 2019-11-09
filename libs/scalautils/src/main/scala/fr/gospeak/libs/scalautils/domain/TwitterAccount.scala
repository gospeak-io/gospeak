package fr.gospeak.libs.scalautils.domain

final case class TwitterAccount(value: Url) extends AnyVal {
  def url: String = value.value

  def handle: String = "@" + value.value.split("/").last
}
