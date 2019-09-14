package fr.gospeak.infra.services.meetup

import fr.gospeak.infra.services.meetup.MeetupSrvImpl._
import fr.gospeak.libs.scalautils.domain.Markdown
import org.scalatest.{FunSpec, Matchers}

class MeetupSrvImplSpec extends FunSpec with Matchers {
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
        toSimpleHtml(Markdown("Here is a [link](https://gospeak.fr)")) shouldBe "Here is a https://gospeak.fr"
      }
      it("should replace images with url") {
        toSimpleHtml(Markdown("Here is an ![image](https://gospeak.fr/icon.png)")) shouldBe "Here is an https://gospeak.fr/icon.png"
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
