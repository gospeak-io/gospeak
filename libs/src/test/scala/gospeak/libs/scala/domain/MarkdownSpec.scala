package gospeak.libs.scala.domain

import gospeak.libs.testingutils.BaseSpec

class MarkdownSpec extends BaseSpec {
  describe("MarkdownUtils") {
    it("should parse and render basic markdown") {
      Markdown(
        """# My title
          |
          |A paragraph with *italic*, **bold** and a list:
          |
          | - item 1
          | - item 2
        """.stripMargin.trim).toHtml shouldBe Html(
        """<div class="markdown"><h1>My title</h1>
          |<p>A paragraph with <em>italic</em>, <strong>bold</strong> and a list:</p>
          |<ul>
          |<li>item 1</li>
          |<li>item 2</li>
          |</ul></div>
        """.stripMargin.trim)
    }
    it("should escape raw html") {
      Markdown(
        """# No html
          |
          |<p>should be escaped</p>
        """.stripMargin.trim).toHtml shouldBe Html(
        """<div class="markdown"><h1>No html</h1>
          |<p>&lt;p&gt;should be escaped&lt;/p&gt;</p></div>
        """.stripMargin.trim)
    }
    it("should allows emoji") {
      Markdown("Use emoji, they are great :scream:").toHtml shouldBe Html(
        """<div class="markdown"><p>Use emoji, they are great 😱</p></div>
        """.stripMargin.trim)
    }
    it("should render as text") {
      Markdown("Hello **world**").toText shouldBe "Hello world"
    }
  }
}
