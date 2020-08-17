package gospeak.libs.scala.domain

/**
 * A simple value class wrapping in memory strings that should not be logged.
 * The `decode` method could be complexified if it needs more security
 */
final case class Secret(private val value: String) extends AnyVal {
  def decode: String = value

  override def toString: String = "Secret(*****)"
}
