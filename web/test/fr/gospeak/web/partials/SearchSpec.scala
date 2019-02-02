package fr.gospeak.web.partials

import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.libs.scalautils.domain.Page._
import org.scalatest.{FunSpec, Matchers}

class SearchSpec extends FunSpec with Matchers {
  private val call = fr.gospeak.web.routes.HomeCtrl.index()

  describe("search.scala.html") {
    it("should display order-by input when present") {
      html.search(buildPage(), call).body should not include s"""<input type="hidden" name="${OrderBy.key}""""
      html.search(buildPage(orderBy = Some("title")), call).body should include(s"""<input type="hidden" name="${OrderBy.key}" value="title">""")
    }
    it("should display page-size input when not default") {
      html.search(buildPage(), call).body should not include s"""<input type="hidden" name="${Size.key}""""
      html.search(buildPage(size = 30), call).body should include(s"""<input type="hidden" name="${Size.key}" value="30">""")
    }
    it("should display search input with search value by default") {
      html.search(buildPage(), call).body should include(s"""<input type="text" class="form-control" id="${Search.key}" name="${Search.key}" value="" placeholder="Search...">""")
      html.search(buildPage(search = Some("test")), call).body should include(s"""<input type="text" class="form-control" id="${Search.key}" name="${Search.key}" value="test" placeholder="Search...">""")
    }
  }

  def buildPage(no: Int = 1, size: Int = 20, search: Option[String] = None, orderBy: Option[String] = None): Page[Int] =
    Page(Seq(), Params(No(no), Size(size), search.map(Search(_)), orderBy.map(OrderBy(_))), Total(1))
}
