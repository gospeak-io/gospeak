package gospeak.web.pages.partials

import gospeak.libs.scala.domain.Page
import gospeak.libs.scala.domain.Page._
import gospeak.web.testingutils.BaseSpec

class SearchSpec extends BaseSpec {
  private val call = gospeak.web.pages.published.routes.HomeCtrl.index()

  describe("partials.search.scala.html") {
    it("should display order-by input when present") {
      html.search(buildPage(), call).body should not include s"""<input type="hidden" name="${OrderBy.key}""""
      html.search(buildPage(orderBy = Some("title")), call).body should include(s"""<input type="hidden" name="${OrderBy.key}" value="title">""")
    }
    it("should display page-size input when not default") {
      html.search(buildPage(), call).body should not include s"""<input type="hidden" name="${Size.key}""""
      html.search(buildPage(size = 22), call).body should include(s"""<input type="hidden" name="${Size.key}" value="22">""")
    }
    it("should display search input with search value by default") {
      html.search(buildPage(), call).body should include(s"""<input type="text" class="form-control" id="${Search.key}" name="${Search.key}" value="" placeholder="Search...">""")
      html.search(buildPage(search = Some("test")), call).body should include(s"""<input type="text" class="form-control" id="${Search.key}" name="${Search.key}" value="test" placeholder="Search...">""")
    }
  }

  def buildPage(no: Int = 2, size: Int = 20, search: Option[String] = None, orderBy: Option[String] = None, total: Int = 25): Page[Int] =
    Page(Seq(123, 2, 83, 1, 30), Params(No(no), Size(size), search.map(Search(_)), orderBy.map(OrderBy(_))), Total(total))
}
