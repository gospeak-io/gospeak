package gospeak.libs.scala.domain

final case class Price(amount: Double, currency: Price.Currency) {
  def value: String = s"$amount $currency"
}

object Price {

  sealed abstract class Currency(val name: String) {
    def value: String = this.toString
  }

  object Currency {

    case object EUR extends Currency("EUR")

    case object USD extends Currency("USD")

    val all: List[Currency] = List(EUR, USD)

    def from(str: String): Option[Currency] =
      all.find(_.value == str)
  }

}
