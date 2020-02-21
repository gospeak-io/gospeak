package gospeak.infra.services

import gospeak.core.domain.utils.TemplateData
import gospeak.core.domain.utils.TemplateData.EventInfo
import gospeak.infra.services.TemplateSrvImplSpec._
import gospeak.libs.scala.domain.MustacheTmpl.MustacheMarkdownTmpl
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, Json}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class TemplateSrvImplSpec extends AnyFunSpec with Matchers {
  private val srv = new TemplateSrvImpl

  describe("MustacheTemplateSrv") {
    it("should render a simple template") {
      MustacheTemplateSrv.render(
        tmpl = "Hello {{name}}",
        data = Json.obj("name" -> Json.fromString("John"))
      ).right.get shouldBe "Hello John"
    }
    it("should render a template with case class") {
      MustacheTemplateSrv.render(
        tmpl = "Hello {{name}}",
        data = Data("John")
      ).right.get shouldBe "Hello John"
    }
    it("should render a complex template") {
      val json = Json.obj(
        "null" -> Json.Null,
        "boolean" -> Json.True,
        "int" -> Json.fromInt(3),
        "double" -> Json.fromDoubleOrNull(3.14),
        "string" -> Json.fromString("aaa"),
        "arr" -> Json.arr(Seq("aaa", "bbb", "ccc").map(Json.fromString): _*),
        "obj" -> Json.obj(
          "aaa" -> Json.fromString("aaa"),
          "bbb" -> Json.fromString("bbb")
        ),
        "arrObj" -> Json.arr(Seq("aaa", "bbb", "ccc").map(v => Json.obj("value" -> Json.fromString(v))): _*)
      )
      val template =
        """null: '{{null}}'
          |boolean: {{boolean}}
          |iftrue: {{#boolean}}show{{/boolean}}
          |int: {{int}}
          |double: {{double}}
          |string: {{string}}
          |arr:{{#arr}} v:{{.}}{{/arr}},
          |obj.aaa: {{obj.aaa}}
          |arrObj:{{#arrObj}} value:{{value}}{{/arrObj}}
        """.stripMargin.trim
      val expected =
        s"""null: ''
           |boolean: true
           |iftrue: show
           |int: 3
           |double: 3.14
           |string: aaa
           |arr: v:aaa v:bbb v:ccc,
           |obj.aaa: aaa
           |arrObj: value:aaa value:bbb value:ccc
         """.stripMargin.trim
      val result = MustacheTemplateSrv.render(template, json)
      result.right.get shouldBe expected
    }
  }
  describe("TemplateSrv") {
    describe("TemplateData.Static") {
      it("should render correctly default event description") {
        val description = MustacheMarkdownTmpl[EventInfo](
          """{{#venue}}
            |This month we are hosted by **{{name}}**, thanks to them :)
            |
            |![{{name}} logo]({{logoUrl}})
            |{{/venue}}
            |
            |
            |{{#talks}}
            |{{#-first}}Here are the talks for this session:{{/-first}}
            |
            |- **{{title}}** by {{#speakers}}*{{name}}*{{^-last}} and {{/-last}}{{/speakers}}
            |
            |{{description.short2}} {{#publicLink}}[see more]({{.}}){{/publicLink}}
            |{{/talks}}
            |
            |
            |---
            |
            |Propose your talks for the next sessions on [Gospeak]({{cfp.publicLink}})
      """.stripMargin.trim)
        val result = srv.render(description, TemplateData.Sample.eventInfo).right.get.value
        result shouldBe
          """This month we are hosted by **Zeenea**, thanks to them :)
            |
            |![Zeenea logo](https://dataxday.fr/wp-content/uploads/2018/01/zeenea-logo.png)
            |
            |Here are the talks for this session:
            |
            |- **The Scala revolution** by *John Doe*
            |
            |Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam auctor odio vitae venenatis porta. Quisque cursus dolor augue, nec pharetra dolor ullamcorper id. [see more](https://gospeak.io/groups/humantalks-paris/talks/28f26543-1ab8-4749-b0ac-786d1bd76888)
            |
            |
            |- **Public speaking for everyone** by *John Doe* and *Jane Doe*
            |
            |Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam auctor odio vitae venenatis porta. Quisque cursus dolor augue, nec pharetra dolor ullamcorper id. [see more](https://gospeak.io/groups/humantalks-paris/talks/28f26543-1ab8-4749-b0ac-786d1bd76666)
            |
            |---
            |
            |Propose your talks for the next sessions on [Gospeak](https://gospeak.io/cfps/humantalks-paris)
          """.stripMargin.trim
      }
      it("should do a nice fallback on error") {
        val tmpl =
          """{{talks}}
            |  {{title}}
            |{{/talks}}
          """.stripMargin
        val result = srv.render(MustacheMarkdownTmpl[TemplateData.EventInfo](tmpl), TemplateData.Sample.eventInfo).right.get.value
        result shouldBe "Invalid mustache template"
      }
    }
  }
}

object TemplateSrvImplSpec {

  case class Data(name: String)

  implicit val dataEncoder: Encoder[Data] = deriveEncoder[Data]
}
