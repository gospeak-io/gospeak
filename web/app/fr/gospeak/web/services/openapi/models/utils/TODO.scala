package fr.gospeak.web.services.openapi.models.utils

final case class TODO(value: Unit) extends AnyVal

object TODO {
  def apply(): TODO = new TODO(())
}
