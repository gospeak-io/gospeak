package fr.gospeak.web.utils

import java.time.Instant

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
    it("should bind & unbind a Instant") {
      forAll { v: Instant =>
        val data = instant.unbind(v)
        instant.bind(data) shouldBe Right(v)
      }
      instant.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      instant.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.datetime"), Seq())))
      instant.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.datetime"), Seq())))
    }
    it("should bind & unbind a FiniteDuration") {
      forAll { v: FiniteDuration =>
        val data = duration.unbind(v)
        duration.bind(data).map(_.toMinutes) shouldBe Right(v.toMinutes)
      }
      duration.bind(Map()) shouldBe Left(Seq(FormError("", Seq("error.required"), Seq())))
      duration.bind(Map("" -> "")) shouldBe Left(Seq(FormError("", Seq("error.number"), Seq())))
      duration.bind(Map("" -> "wrong")) shouldBe Left(Seq(FormError("", Seq("error.number"), Seq())))
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
  }
}
