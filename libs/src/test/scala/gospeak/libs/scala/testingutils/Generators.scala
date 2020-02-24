package gospeak.libs.scala.testingutils

import gospeak.libs.scala.domain
import gospeak.libs.scala.domain.TimePeriod
import gospeak.libs.scala.domain.TimePeriod.PeriodUnit
import org.scalacheck.{Arbitrary, Gen}

import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.util.Try

object Generators {
  private def buildDuration(length: Long, unit: TimeUnit): FiniteDuration = Try(new FiniteDuration(length, unit)).getOrElse(buildDuration(length / 2, unit))

  implicit val aFiniteDuration: Arbitrary[FiniteDuration] = Arbitrary(for {
    length <- implicitly[Arbitrary[Long]].arbitrary
    unit <- implicitly[Arbitrary[TimeUnit]].arbitrary
  } yield buildDuration(length, unit))
  implicit val aPeriodUnit: Arbitrary[PeriodUnit] = Arbitrary(Gen.oneOf(PeriodUnit.all))
  implicit val aTimePeriod: Arbitrary[TimePeriod] = Arbitrary(for {
    length <- implicitly[Arbitrary[Long]].arbitrary
    unit <- implicitly[Arbitrary[PeriodUnit]].arbitrary
  } yield domain.TimePeriod(length, unit))
}
