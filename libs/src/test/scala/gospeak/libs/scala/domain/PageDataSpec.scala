package gospeak.libs.scala.domain

import gospeak.libs.scala.domain.PageData._
import gospeak.libs.testingutils.BaseSpec

class PageDataSpec extends BaseSpec {
  private val metas = Map(
    "title" -> List(RowItem("t1"), RowItem("t2")),
    "og:title" -> List(RowItem("t3"), RowItem("t4")),
    "og:description" -> List(RowItem("d1")),
    "og:image" -> List(RowItem("img1")),
    "author" -> List(RowItem("au1")),
    "icon" -> List(RowItem("i1", Map("sizes" -> "50x50")), RowItem("i2")),
    "theme-color" -> List(RowItem("#000")),
    "keywords" -> List(RowItem("k1,k2")),
    "canonical" -> List(RowItem("c1")),
    "og:site_name" -> List(RowItem("sn1")))

  describe("PageData") {
    it("should extract the page title") {
      getTitle(metas) shouldBe Some("t1")
    }
    it("should extract the page description") {
      getDescription(metas) shouldBe Some("d1")
    }
    it("should extract the page image") {
      getImage(metas) shouldBe Some("img1")
    }
    it("should extract the page author") {
      getAuthor(metas) shouldBe Some("au1")
    }
    it("should extract the page icons") {
      getIcons(metas) shouldBe List(SizedItem("i1", Some(Size(50, 50))), SizedItem("i2", None))
    }
    it("should extract the page color") {
      getColor(metas) shouldBe Some("#000")
    }
    it("should extract the page keywords") {
      getKeywords(metas) shouldBe List("k1", "k2")
    }
    it("should extract the page canonical url") {
      getCanonical(metas) shouldBe Some("c1")
    }
    it("should extract the page site name") {
      getSiteName(metas) shouldBe Some("sn1")
    }
    it("should extract the first found value") {
      getFirst(metas, List("twitter:title", "og:title", "title")) shouldBe Some(FullItem("og:title", "t3", Map()))
    }
    it("should extract all found values") {
      getAll(metas, List("twitter:title", "og:title", "title")) shouldBe List(
        FullItem("og:title", "t3", Map()),
        FullItem("og:title", "t4", Map()),
        FullItem("title", "t1", Map()),
        FullItem("title", "t2", Map()))
    }
    it("should parse sizes") {
      parseSize("48x48") shouldBe Some(Size(48, 48))
      parseSize("abc") shouldBe None
    }
  }
}
