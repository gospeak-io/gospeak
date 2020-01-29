package gospeak.infra.services

import gospeak.libs.scala.domain.{Html, Markdown}
import org.scalatest.{FunSpec, Matchers}

class MarkdownSrvImplSpec extends FunSpec with Matchers {
  describe("MarkdownUtils") {
    it("should parse and render basic markdown") {
      MarkdownSrvImpl.render(Markdown(
        """# My title
          |
          |A paragraph with *italic*, **bold** and a list:
          |
          | - item 1
          | - item 2
        """.stripMargin.trim)) shouldBe Html(
        """<div class="markdown "><h1>My title</h1>
          |<p>A paragraph with <em>italic</em>, <strong>bold</strong> and a list:</p>
          |<ul>
          |<li>item 1</li>
          |<li>item 2</li>
          |</ul></div>
        """.stripMargin.trim)
    }
    it("should escape raw html") {
      MarkdownSrvImpl.render(Markdown(
        """# No html
          |
          |<p>should be escaped</p>
        """.stripMargin.trim)) shouldBe Html(
        """<div class="markdown "><h1>No html</h1>
          |<p>&lt;p&gt;should be escaped&lt;/p&gt;</p></div>
        """.stripMargin.trim)
    }
    it("should allows emoji") {
      MarkdownSrvImpl.render(Markdown("Use emoji, they are great :scream:")) shouldBe Html(
        """<div class="markdown "><p>Use emoji, they are great 😱</p></div>
        """.stripMargin.trim)
    }
  }
}
