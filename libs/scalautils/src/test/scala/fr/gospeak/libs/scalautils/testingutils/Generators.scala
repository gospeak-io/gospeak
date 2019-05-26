package fr.gospeak.libs.scalautils.testingutils

import java.time.temporal.ChronoUnit

import fr.gospeak.libs.scalautils.domain.TimePeriod
import org.scalacheck.Arbitrary

import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.util.Try

object Generators {
  private def buildDuration(length: Long, unit: TimeUnit): FiniteDuration = Try(new FiniteDuration(length, unit)).getOrElse(buildDuration(length / 2, unit))

  implicit val aFiniteDuration: Arbitrary[FiniteDuration] = Arbitrary(for {
    length <- implicitly[Arbitrary[Long]].arbitrary
    unit <- implicitly[Arbitrary[TimeUnit]].arbitrary
  } yield buildDuration(length, unit))
  implicit val aTimePeriod: Arbitrary[TimePeriod] = Arbitrary(for {
    length <- implicitly[Arbitrary[Long]].arbitrary
    unit <- implicitly[Arbitrary[ChronoUnit]].arbitrary
  } yield TimePeriod(length, unit))
}
