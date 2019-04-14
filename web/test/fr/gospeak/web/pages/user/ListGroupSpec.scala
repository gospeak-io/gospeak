package fr.gospeak.web.pages.user

import fr.gospeak.core.domain.Group
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.testingutils.TwirlSpec

class ListGroupSpec extends TwirlSpec {
  private val groups = random[Group](10)

  describe("list.scala.html") {
    it("should display a jumbotron on empty page") {
      html.listGroup(Page.empty[Group])(b).toString should include("""<div class="jumbotron">""")
    }
    it("should display a list when non empty page") {
      val res = html.listGroup(Page.from(groups))(b).toString
      res should not include """<div class="jumbotron">"""
      res should include("""<div class="list-group mt-3 mb-3">""")
      res should include(groups.head.name.value)
    }
  }
}
