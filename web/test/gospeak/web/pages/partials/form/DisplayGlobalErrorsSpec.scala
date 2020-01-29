package gospeak.web.pages.partials.form

import gospeak.web.testingutils.TwirlSpec
import play.api.data.Form
import play.api.data.Forms._

class DisplayGlobalErrorsSpec extends TwirlSpec {
  private val form: Form[String] = Form(mapping(
    "text" -> text
  )(identity)(Some(_)))

  describe("pages.partials.form.displayGlobalErrors.scala.html") {
    it("should display nothing when no errors") {
      val res = html.displayGlobalErrors(form)(userReq).body.trim
      res shouldBe ""
    }
    it("should display the error text when just one error") {
      val f = form.withGlobalError("error alone")
      val res = html.displayGlobalErrors(f)(userReq).body.trim
      res should include("error alone")
    }
    it("should display the list of errors when multiple errors") {
      val f = form.withGlobalError("error 1").withGlobalError("error 2")
      val res = html.displayGlobalErrors(f)(userReq).body.trim
      res should include("error 1")
      res should include("error 2")
    }
  }
}
