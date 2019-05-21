package fr.gospeak.infra.services

import fr.gospeak.infra.services.TemplateSrvSpec._
import fr.gospeak.libs.scalautils.domain.MarkdownTemplate._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, Json}
import org.scalatest.{FunSpec, Matchers}

class TemplateSrvSpec extends FunSpec with Matchers {
  describe("MustacheTemplateSrv") {
    it("should render a simple template") {
      MustacheTemplateSrv.render(
        tmpl = Mustache("Hello {{name}}"),
        data = Json.obj("name" -> Json.fromString("John"))
      ).right.get.value shouldBe "Hello John"
    }
    it("should render a template with case class") {
      MustacheTemplateSrv.render(
        tmpl = Mustache("Hello {{name}}"),
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
      val template = Mustache(
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
}

object TemplateSrvSpec {

  case class Data(name: String)

  implicit val dataEncoder: Encoder[Data] = deriveEncoder[Data]
}
