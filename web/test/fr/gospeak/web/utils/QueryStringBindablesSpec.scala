package fr.gospeak.web.utils

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.libs.scalautils.domain.{Page, Url}
import fr.gospeak.web.utils.QueryStringBindables._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class QueryStringBindablesSpec extends AnyFunSpec with Matchers {
  describe("QueryStringBindables") {
    it("should bind & unbind a Page.Params when no params") {
      val params = Page.Params()
      pageParamsQueryStringBindable.bind("", Map()) shouldBe Some(Right(params))
      pageParamsQueryStringBindable.unbind("", params) shouldBe ""
    }
    it("should bind & unbind a Page.Params when all params") {
      val params = buildParams(2, 30, "test", "name")
      pageParamsQueryStringBindable.bind("", Map(
        Page.No.key -> Seq("2"),
        Page.Size.key -> Seq("30"),
        Page.Search.key -> Seq("test"),
        Page.OrderBy.key -> Seq("name"))) shouldBe Some(Right(params))
      pageParamsQueryStringBindable.unbind("", params) shouldBe s"${Page.No.key}=2&${Page.Size.key}=30&${Page.Search.key}=test&${Page.OrderBy.key}=name"
    }
    it("should bind & unbind a Url") {
      val url = Url.from("http://youtube.com").right.get
      urlQueryStringBindable.bind("key", Map("key" -> Seq("http://youtube.com"))) shouldBe Some(Right(url))
      urlQueryStringBindable.unbind("key", url) shouldBe s"key=http%3A%2F%2Fyoutube.com"
    }
    it("should bind & unbind a UserRequest.Id") {
      val id = UserRequest.Id.generate()
      userRequestIdQueryStringBindable.bind("key", Map("key" -> Seq(id.value))) shouldBe Some(Right(id))
      userRequestIdQueryStringBindable.unbind("key", id) shouldBe s"key=${id.value}"
    }
  }

  private def buildParams(no: Int, size: Int, search: String, order: String) =
    Page.Params(Page.No(no), Page.Size(size), Some(Page.Search(search)), Some(Page.OrderBy(order)))
}
