package gospeak.libs.scala.domain

import org.scalatest.{FunSpec, Matchers}

class PageSpec extends FunSpec with Matchers {
  describe("Page") {
    describe("hasManyPages") {
      it("should return true when total > size") {
        page(no = 1, size = 5, total = 4).hasManyPages shouldBe false
        page(no = 1, size = 5, total = 9).hasManyPages shouldBe true
      }
    }
    describe("isFirst") {
      it("should return true when no is 1") {
        page(no = 1, size = 5, total = 4).isFirst shouldBe true
        page(no = 2, size = 5, total = 9).isFirst shouldBe false
      }
    }
    describe("isLast") {
      it("should return true when no is last") {
        page(no = 1, size = 5, total = 4).isLast shouldBe true
        page(no = 1, size = 5, total = 9).isLast shouldBe false
        page(no = 2, size = 5, total = 9).isLast shouldBe true
      }
    }
    describe("previous") {
      it("should return params for previous page") {
        page(no = 2, size = 5, total = 9).previous shouldBe params(no = 1, size = 5)
      }
      it("should return same params when first page") {
        page(no = 1, size = 5, total = 9).previous shouldBe params(no = 1, size = 5)
      }
    }
    describe("next") {
      it("should return params for next page") {
        page(no = 1, size = 5, total = 9).next shouldBe params(no = 2, size = 5)
      }
      it("should return same params when last next") {
        page(no = 2, size = 5, total = 9).next shouldBe params(no = 2, size = 5)
      }
    }
    describe("isCurrent") {
      it("should return true when same page no") {
        page(no = 1, size = 5, total = 9).isCurrent(params(no = 1, size = 5)) shouldBe true
        page(no = 2, size = 5, total = 9).isCurrent(params(no = 1, size = 5)) shouldBe false
      }
    }
    describe("firstPages") {
      it("should return first pages to show when current page is not in the first pages") {
        page(no = 1, size = 5, total = 103).firstPages shouldBe None
        page(no = 6, size = 5, total = 103).firstPages shouldBe None
        page(no = 7, size = 5, total = 103).firstPages.map(_.map(_.page.value)) shouldBe Some(Seq(1, 2))
        page(no = 21, size = 5, total = 103).firstPages.map(_.map(_.page.value)) shouldBe Some(Seq(1, 2))
      }
    }
    describe("lastPages") {
      it("should return last pages to show when current page is not in the last pages") {
        page(no = 1, size = 5, total = 103).lastPages.map(_.map(_.page.value)) shouldBe Some(Seq(20, 21))
        page(no = 15, size = 5, total = 103).lastPages.map(_.map(_.page.value)) shouldBe Some(Seq(20, 21))
        page(no = 16, size = 5, total = 103).lastPages shouldBe None
        page(no = 21, size = 5, total = 103).lastPages shouldBe None
      }
    }
    describe("middlePages") {
      it("should return pages to show next to the current one") {
        page(no = 1, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(1, 2, 3)
        page(no = 2, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(1, 2, 3, 4)
        page(no = 3, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(1, 2, 3, 4, 5)
        page(no = 4, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(1, 2, 3, 4, 5, 6)
        page(no = 5, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(1, 2, 3, 4, 5, 6, 7)
        page(no = 6, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(1, 2, 3, 4, 5, 6, 7, 8)
        page(no = 7, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(5, 6, 7, 8, 9)
        page(no = 8, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(6, 7, 8, 9, 10)
        page(no = 9, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(7, 8, 9, 10, 11)
        page(no = 10, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(8, 9, 10, 11, 12)
        page(no = 11, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(9, 10, 11, 12, 13)
        page(no = 12, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(10, 11, 12, 13, 14)
        page(no = 13, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(11, 12, 13, 14, 15)
        page(no = 14, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(12, 13, 14, 15, 16)
        page(no = 15, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(13, 14, 15, 16, 17)
        page(no = 16, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(14, 15, 16, 17, 18, 19, 20, 21)
        page(no = 17, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(15, 16, 17, 18, 19, 20, 21)
        page(no = 18, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(16, 17, 18, 19, 20, 21)
        page(no = 19, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(17, 18, 19, 20, 21)
        page(no = 20, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(18, 19, 20, 21)
        page(no = 21, size = 5, total = 103).middlePages.map(_.page.value) shouldBe Seq(19, 20, 21)
      }
    }
    it("should not have too much items") {
      an[AssertionError] should be thrownBy page(no = 1, size = 3, total = 2)
    }
  }

  def params(no: Int, size: Int) = Page.Params(Page.No(no), Page.Size(size))

  def page(no: Int, size: Int, total: Int) = Page(Seq(1, 5, 3, 4, 2), params(no, size), Page.Total(total), Seq())
}
