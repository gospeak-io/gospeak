package fr.gospeak.web.utils

import fr.gospeak.libs.scalautils.domain.Page._
import fr.gospeak.web.utils.QueryStringBindables._
import org.scalatest.{FunSpec, Matchers}

class QueryStringBindablesSpec extends FunSpec with Matchers {
  describe("QueryStringBindables") {
    describe("pageParamsQueryStringBindable") {
      it("should bind & unbind with no params") {
        val params = Params()
        pageParamsQueryStringBindable.bind("", Map()) shouldBe Some(Right(params))
        pageParamsQueryStringBindable.unbind("", params) shouldBe ""
      }
      it("should bind & unbind with all params") {
        val params = buildParams(2, 30, "test", "name")
        pageParamsQueryStringBindable.bind("", Map(
          No.key -> Seq("2"),
          Size.key -> Seq("30"),
          Search.key -> Seq("test"),
          OrderBy.key -> Seq("name"))) shouldBe Some(Right(params))
        pageParamsQueryStringBindable.unbind("", params) shouldBe s"${No.key}=2&${Size.key}=30&${Search.key}=test&${OrderBy.key}=name"
      }
    }
  }

  def buildParams(no: Int, size: Int, search: String, order: String) =
    Params(No(no), Size(size), Some(Search(search)), Some(OrderBy(order)))
}
