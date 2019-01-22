package fr.gospeak.core.domain.utils

final case class Password(private val value: String) extends AnyVal {
  def decode: String = value

  override def toString: String = "*****"
}
