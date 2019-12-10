package fr.gospeak.web.utils

import java.net.URLEncoder
import java.time.{LocalDate, LocalDateTime}

import fr.gospeak.core.domain.UserRequest
import fr.gospeak.libs.scalautils.domain.{Page, Url}
import fr.gospeak.web.utils.QueryStringBindables._
import org.scalatest.{FunSpec, Matchers}

class QueryStringBindablesSpec extends FunSpec with Matchers {
  describe("QueryStringBindables") {
    describe("LocalDateTime") {
      it("should parse and format dates") {
        val ldt = LocalDateTime.of(2019, 9, 21, 19, 12)
        val date = "21/09/2019"
        val dateTime = s"$date 19:12"
        val dateEncoded = URLEncoder.encode(date, "UTF-8")
        val dateTimeEncoded = URLEncoder.encode(dateTime, "UTF-8")
        LocalDateTime.parse(dateTime, dtf1) shouldBe ldt
        LocalDateTime.parse(dateTimeEncoded, dtf2) shouldBe ldt
        LocalDate.parse(date, df1).atTime(19, 12) shouldBe ldt
        LocalDate.parse(dateEncoded, df2).atTime(19, 12) shouldBe ldt
        ldt.format(df1) shouldBe date
      }
      it("should bind & unbind a LocalDateTime when no params") {
        val ldt = LocalDateTime.of(2019, 9, 21, 0, 0)
        val date = "21/09/2019"
        val dateTimeEncoded = URLEncoder.encode(date + " 00:00", "UTF-8")
        localDateTimeQueryStringBindable.bind("key", Map("key" -> Seq(date))) shouldBe Some(Right(ldt))
        localDateTimeQueryStringBindable.unbind("key", ldt) shouldBe s"key=$dateTimeEncoded"
      }
    }
    describe("Page.Params") {
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
      it("should bind & unbind filters") {
        val params = Page.Params.defaults.toggleFilter("f1").withFilter("f2", "v2")
        pageParamsQueryStringBindable.bind("", Map(
          "f1" -> Seq("true"),
          "f2" -> Seq("v2"))) shouldBe Some(Right(params))
        pageParamsQueryStringBindable.unbind("", params) shouldBe s"f1=true&f2=v2"
      }
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
