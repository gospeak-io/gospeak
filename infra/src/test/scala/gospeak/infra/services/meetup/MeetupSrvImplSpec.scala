package gospeak.infra.services.meetup

import gospeak.infra.services.meetup.MeetupSrvImpl._
import gospeak.libs.scala.domain.Markdown
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MeetupSrvImplSpec extends AnyFunSpec with Matchers {
  describe("MeetupSrvImpl") {
    describe("toSimpleHtml") {
      it("should replace ** with <b>") {
        toSimpleHtml(Markdown("Hello **Loïc**")) shouldBe "Hello <b>Loïc</b>"
        toSimpleHtml(Markdown(
          """A multiline **text** using
            |**different** bold
            |in many **places**
            |""".stripMargin)) shouldBe
          """A multiline <b>text</b> using
            |<b>different</b> bold
            |in many <b>places</b>
            |""".stripMargin
      }
      it("should replace * with <i>") {
        toSimpleHtml(Markdown("Hello *Loïc*")) shouldBe "Hello <i>Loïc</i>"
        toSimpleHtml(Markdown(
          """A multiline *text* using
            |*different* italic
            |in many *places*
            |""".stripMargin)) shouldBe
          """A multiline <i>text</i> using
            |<i>different</i> italic
            |in many <i>places</i>
            |""".stripMargin
      }
      it("should replace links with <a>") {
        toSimpleHtml(Markdown("Here is a [link](https://gospeak.io)")) shouldBe "Here is a https://gospeak.io"
      }
      it("should replace images with url") {
        toSimpleHtml(Markdown("Here is an ![image](https://gospeak.io/icon.png)")) shouldBe "Here is an https://gospeak.io/icon.png"
      }
      it("should handle a full text") {
        val md = Markdown(
          """Hi everyone, welcome to **HumanTalks Paris Novembre 2019**!
            |
            |
            |This month we are hosted by **Zeenea**, at *[48 Rue de Ponthieu, 75008 Paris, France](https://maps.google.com/?cid=3360768160548514744)*
            |
            |![Zeenea logo](https://www.freelogodesign.org/Content/img/logo-ex-1.png)
            |
            |
            |For this session we are happy to have the following talks:
            |
            |- **Why FP** by *Demo User*
            |
            |Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.
            |
            |For the next sessions, propose your talks on [Gospeak](http://localhost:9000/cfps/ht-paris)
            |""".stripMargin)
        toSimpleHtml(md) shouldBe
          """Hi everyone, welcome to <b>HumanTalks Paris Novembre 2019</b>!
            |
            |
            |This month we are hosted by <b>Zeenea</b>, at <i>https://maps.google.com/?cid=3360768160548514744</i>
            |
            |https://www.freelogodesign.org/Content/img/logo-ex-1.png
            |
            |
            |For this session we are happy to have the following talks:
            |
            |- <b>Why FP</b> by <i>Demo User</i>
            |
            |Cras sit amet nibh libero, in gravida nulla. Nulla vel metus scelerisque ante sollicitudin.
            |
            |For the next sessions, propose your talks on http://localhost:9000/cfps/ht-paris
            |""".stripMargin
      }
    }
  }
}
