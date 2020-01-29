package gospeak.web.pages.partials.form

import gospeak.web.testingutils.TwirlSpec
import play.api.data.Forms._
import play.api.data.{Field, Form}

class InputLocalTimeSpec extends TwirlSpec {
  private val form: Form[String] = Form(mapping(
    "text" -> text
  )(identity)(Some(_)))
  private val field: Field = form("text")

  describe("pages.partials.form.inputLocalTime.scala.html") {
    it("should render HTML") {
      val res = html.inputLocalTime(field).body.trim
      res should include("input-time")
    }
  }
}
