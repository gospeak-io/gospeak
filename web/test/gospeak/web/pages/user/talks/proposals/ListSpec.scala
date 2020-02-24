package gospeak.web.pages.user.talks.proposals

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import gospeak.core.domain.{CommonProposal, Talk}
import gospeak.core.testingutils.Generators._
import gospeak.libs.scala.domain.Page
import gospeak.web.testingutils.TwirlSpec

class ListSpec extends TwirlSpec with RandomDataGenerator {
  private val talk = random[Talk]
  private val proposals = random[CommonProposal](10)

  describe("user.talks.proposals.list.scala.html") {
    it("should display a jumbotron on empty page") {
      html.list(talk, Page.empty[CommonProposal])(b).toString should include("""<div class="jumbotron">""")
    }
    it("should display a list when non empty page") {
      val res = html.list(talk, Page(proposals, Page.Params.defaults, Page.Total(proposals.length), Seq()))(b).toString
      res should not include """<div class="jumbotron">"""
      res should include("""<div class="list-group mb-3">""")
      res should include(proposals.head.title.value)
    }
  }
}
