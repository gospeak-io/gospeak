package fr.gospeak.libs.scalautils.domain

final case class Secret(private val value: String) extends AnyVal {
  def decode: String = value

  override def toString: String = "*****"
}
