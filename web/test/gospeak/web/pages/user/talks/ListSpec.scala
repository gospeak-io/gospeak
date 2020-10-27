package gospeak.web.pages.user.talks

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import gospeak.core.domain._
import gospeak.core.testingutils.Generators._
import gospeak.web.testingutils.TwirlSpec
import gospeak.libs.scala.domain.Page

class ListSpec extends TwirlSpec with RandomDataGenerator {
  private val talks = random[Talk](10).toList

  describe("speaker.talks.list.scala.html") {
    it("should display a jumbotron on empty page") {
      html.list(Page.empty[Talk])(b).toString should include("""<div class="jumbotron">""")
    }
    it("should display a list when non empty page") {
      val res = html.list(Page(talks, Page.Params.defaults, Page.Total(talks.length)))(b).toString
      res should not include """<div class="jumbotron">"""
      res should include("""<div class="list-group mb-3">""")
      res should include(talks.head.title.value)
    }
  }
}
