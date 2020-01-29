package gospeak.libs.openapi.models.utils

final case class TODO(value: Unit) extends AnyVal

object TODO {
  def apply(): TODO = new TODO(())
}
