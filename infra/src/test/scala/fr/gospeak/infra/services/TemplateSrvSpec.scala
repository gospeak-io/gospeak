package fr.gospeak.infra.services

import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.infra.services.TemplateSrvSpec._
import fr.gospeak.libs.scalautils.domain.MarkdownTemplate._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, Json}
import org.scalatest.{FunSpec, Matchers}

class TemplateSrvSpec extends FunSpec with Matchers {
  private val srv = new TemplateSrv

  describe("MustacheTemplateSrv") {
    it("should render a simple template") {
      MustacheTemplateSrv.render(
        tmpl = Mustache[Json]("Hello {{name}}"),
        data = Json.obj("name" -> Json.fromString("John"))
      ).right.get.value shouldBe "Hello John"
    }
    it("should render a template with case class") {
      MustacheTemplateSrv.render(
        tmpl = Mustache[Data]("Hello {{name}}"),
        data = Data("John")
      ).right.get.value shouldBe "Hello John"
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
      val template = Mustache[Json](
        """null: '{{null}}'
          |boolean: {{boolean}}
          |iftrue: {{#boolean}}show{{/boolean}}
          |int: {{int}}
          |double: {{double}}
          |string: {{string}}
          |arr:{{#arr}} v:{{.}}{{/arr}},
          |obj.aaa: {{obj.aaa}}
          |arrObj:{{#arrObj}} value:{{value}}{{/arrObj}}
        """.stripMargin.trim)
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
      result.right.get.value shouldBe expected
    }
  }
  describe("TemplateSrv") {
    describe("TemplateData.Static") {
      it("should render correctly default event description") {
        val result = srv.render(TemplateData.Static.defaultEventDescription, TemplateData.Sample.eventInfo).right.get.value
        result shouldBe
          """Hi everyone, welcome to **HumanTalks Paris Septembre**!
            |
            |
            |This month we are hosted by **Zeenea**, at *[48 Rue de Ponthieu, 75008 Paris](https://maps.google.com/?cid=3360768160548514744)*
            |
            |![Zeenea logo](https://www.zeenea.com/wp-content/uploads/2019/01/zeenea-logo-424x112-1.png)
            |
            |
            |
            |
            |For this session we are happy to have the following talks:
            |
            |- **The Scala revolution** by *John Doe*
            |
            |Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam auctor odio vitae venenatis porta. Quisque cursus dolor augue, nec pharetra dolor ullamcorper id. [see more](https://gospeak.fr/groups/humantalks-paris/talks/28f26543-1ab8-4749-b0ac-786d1bd76888)
            |
            |For the next sessions, propose your talks on [Gospeak](https://gospeak.fr/cfps/humantalks-paris)
          """.stripMargin.trim
      }
    }
  }
}

object TemplateSrvSpec {

  case class Data(name: String)

  implicit val dataEncoder: Encoder[Data] = deriveEncoder[Data]
}
