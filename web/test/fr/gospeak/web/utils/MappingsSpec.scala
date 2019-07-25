package fr.gospeak.web.utils

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}
import java.util.concurrent.TimeUnit

import fr.gospeak.core.domain._
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.web.utils.Mappings._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSpec, Matchers}
import play.api.data.FormError

import scala.concurrent.duration.FiniteDuration

class MappingsSpec extends FunSpec with Matchers with PropertyChecks {
  describe("Mappings") {
    it("should bind & unbind a Double") {
      forAll { v: Double =>
        val data = double.unbind(v)
        double.bind(data) shouldBe Right(v)
      }
      double.bind(Map("" -> "12.3")) shouldBe Right(12.3)
      double.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      double.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.real"), Seq())))
      double.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.real"), Seq())))
    }
    it("should bind & unbind a Instant") {
      forAll { v: Instant =>
        val data = instant.unbind(v)
        instant.bind(data) shouldBe Right(v)
      }
      instant.bind(Map("" -> "2019-07-14T15:53:18")) shouldBe Right(Instant.ofEpochMilli(1563119598000L))
      instant.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      instant.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.datetime"), Seq())))
      instant.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.datetime"), Seq())))
    }
    it("should bind & unbind a LocalDateTime") {
      forAll { v: LocalDateTime =>
        val in = v
          .minusNanos(v.getNano) // do not manage nanos
          .plusYears(if (v.getYear == 0) 1 else 0) // do not handle year 0, it's serialized as 1

        val data = myLocalDateTime.unbind(in)
        myLocalDateTime.bind(data) shouldBe Right(in)
      }
      myLocalDateTime.bind(Map("date" -> "14/07/2019", "time" -> "17:45:33")) shouldBe Right(LocalDateTime.of(2019, 7, 14, 17, 45, 33))
    }
    it("should bind & unbind a ChronoUnit") {
      forAll { v: ChronoUnit =>
        val data = chronoUnit.unbind(v)
        chronoUnit.bind(data) shouldBe Right(v)
      }
      chronoUnit.bind(Map("" -> "MINUTES")) shouldBe Right(ChronoUnit.MINUTES)
    }
    it("should bind & unbind a TimePeriod") {
      forAll { v: TimePeriod =>
        val data = period.unbind(v)
        period.bind(data) shouldBe Right(v)
      }
      period.bind(Map("length" -> "12", "unit" -> "Hour")) shouldBe Right(TimePeriod(12, TimePeriod.PeriodUnit.Hour))
    }
    it("should bind & unbind a TimeUnit") {
      forAll { v: TimeUnit =>
        val data = timeUnit.unbind(v)
        timeUnit.bind(data) shouldBe Right(v)
      }
      timeUnit.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      timeUnit.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
      timeUnit.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
    }
    it("should bind & unbind a FiniteDuration") {
      forAll { v: FiniteDuration =>
        val data = duration.unbind(v)
        duration.bind(data).map(_.toMinutes) shouldBe Right(v.toMinutes)
      }
      duration.bind(Map()) shouldBe Left(Seq(FormError("length", Seq("error.required"), Seq()), FormError("unit", Seq("error.required"), Seq())))
      duration.bind(Map("length" -> "", "unit" -> "")) shouldBe Left(Seq(FormError("length", Seq("error.number"), Seq()), FormError("unit", Seq("error.format"), Seq())))
      duration.bind(Map("length" -> "wrong", "unit" -> "wrong")) shouldBe Left(Seq(FormError("length", Seq("error.number"), Seq()), FormError("unit", Seq("error.format"), Seq())))
    }
    it("should bind & unbind a EmailAddress") {
      forAll { v: EmailAddress =>
        val data = emailAddress.unbind(v)
        emailAddress.bind(data) shouldBe Right(v)
      }
      emailAddress.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      emailAddress.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.email"), Seq())))
      emailAddress.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.email"), Seq())))
    }
    it("should bind & unbind a Url") {
      forAll { v: Url =>
        val data = url.unbind(v)
        url.bind(data) shouldBe Right(v)
      }
      url.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      url.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
      url.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
    }
    it("should bind & unbind a Slides") {
      forAll { v: Slides =>
        val data = slides.unbind(v)
        slides.bind(data) shouldBe Right(v)
      }
      slides.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      slides.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
      slides.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
    }
    it("should bind & unbind a Video") {
      forAll { v: Video =>
        val data = video.unbind(v)
        video.bind(data) shouldBe Right(v)
      }
      video.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      video.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
      video.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.format"), Seq())))
    }
    it("should bind & unbind a Secret") {
      forAll { v: Secret =>
        val data = secret.unbind(v)
        secret.bind(data) shouldBe Right(v)
      }
      secret.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
    }
    it("should bind & unbind a Markdown") {
      forAll { v: Markdown =>
        val data = markdown.unbind(v)
        markdown.bind(data) shouldBe Right(v)
      }
      markdown.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
    }
    it("should bind & unbind a Currency") {
      forAll { v: Price.Currency =>
        val data = currency.unbind(v)
        currency.bind(data) shouldBe Right(v)
      }
      currency.bind(Map("" -> "EUR")) shouldBe Right(Price.Currency.EUR)
      currency.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
    }
    it("should bind & unbind a Price") {
      forAll { v: Price =>
        val data = price.unbind(v)
        price.bind(data) shouldBe Right(v)
      }
      price.bind(Map("amount" -> "5.8", "currency" -> "EUR")) shouldBe Right(Price(5.8, Price.Currency.EUR))
    }
    it("should bind & unbind a GMapPlace") {
      forAll { v: GMapPlace =>
        val data = gMapPlace.unbind(v)
        gMapPlace.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a User.Slug") {
      forAll { v: User.Slug =>
        val data = userSlug.unbind(v)
        userSlug.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Group.Slug") {
      forAll { v: Group.Slug =>
        val data = groupSlug.unbind(v)
        groupSlug.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Group.Name") {
      forAll { v: Group.Name =>
        val data = groupName.unbind(v)
        groupName.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Event.Slug") {
      forAll { v: Event.Slug =>
        val data = eventSlug.unbind(v)
        eventSlug.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Event.Name") {
      forAll { v: Event.Name =>
        val data = eventName.unbind(v)
        eventName.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Cfp.Slug") {
      forAll { v: Cfp.Slug =>
        val data = cfpSlug.unbind(v)
        cfpSlug.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Cfp.Name") {
      forAll { v: Cfp.Name =>
        val data = cfpName.unbind(v)
        cfpName.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Talk.Slug") {
      forAll { v: Talk.Slug =>
        val data = talkSlug.unbind(v)
        talkSlug.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Talk.Title") {
      forAll { v: Talk.Title =>
        val data = talkTitle.unbind(v)
        talkTitle.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Template") {
      forAll { v: MarkdownTemplate[Any] =>
        val data = template.unbind(v)
        template.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Group.Settings.Events.Event") {
      forAll { v: Group.Settings.Action.Trigger =>
        val data = groupSettingsEvent.unbind(v)
        groupSettingsEvent.bind(data) shouldBe Right(v)
      }
    }
    it("should bind & unbind a Group.Settings.Events.Action") {
      forAll { v: Group.Settings.Action =>
        val data = groupSettingsAction.unbind(v)
        groupSettingsAction.bind(data) shouldBe Right(v)
      }
    }
  }
}
