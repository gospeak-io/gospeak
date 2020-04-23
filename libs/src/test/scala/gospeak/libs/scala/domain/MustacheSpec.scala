package gospeak.libs.scala.domain

import gospeak.libs.scala.Extensions._
import gospeak.libs.testingutils.BaseSpec
import io.circe.Json

class MustacheSpec extends BaseSpec {
  describe("Mustache") {
    describe("render") {
      it("should render a template") {
        Mustache.render("Hello {{name}}", obj("name" -> js("Loïc"))) shouldBe Right("Hello Loïc")
      }
      it("should fail on missing keys") {
        Mustache.render("Hello {{name}}", obj()) shouldBe Left(Mustache.Error("Missing keys: .name"))
        Mustache.render("Hello {{user.name}}", obj()) shouldBe Left(Mustache.Error("Missing keys: .user"))
        // Mustache.render("Hello {{user.name}}", js("user" -> js())) shouldBe Left(Mustache.Error("Missing keys: .user"))
        Mustache.render("Hello {{user.name}}", obj("user" -> obj("id" -> js("1")))) shouldBe Left(Mustache.Error("Missing keys: .user.name, .name"))
        // Mustache.render("Hello {{#user}}{{name}}{{/user}}", js("user" -> js())) shouldBe Left(Mustache.Error("Missing keys: .user.name"))
        Mustache.render("Hello {{#user}}{{name}}{{/user}}", obj("user" -> obj("id" -> js("1")))) shouldBe Left(Mustache.Error("Missing keys: .user.name, .name"))
      }
      it("should fail on invalid template") {
        Mustache.render("Hello {{name}", obj()) shouldBe Left(Mustache.Error("Error in template near 8: name}"))
      }
      it("should handle special keys") {
        Mustache.render("Hello {{#users}}{{name}}{{^-last}}, {{/-last}}{{/users}}", obj(
          "users" -> arr(
            obj("name" -> js("John")),
            obj("name" -> js("Jane")),
            obj("name" -> js("Jill")))
        )) shouldBe Right("Hello John, Jane, Jill")
      }
      it("should render a complex template") {
        val json = obj(
          "null" -> Json.Null,
          "boolean" -> js(true),
          "int" -> js(3),
          "double" -> js(3.14),
          "string" -> js("aaa"),
          "arr" -> arr(Seq("aaa", "bbb", "ccc").map(js): _*),
          "obj" -> obj(
            "aaa" -> js("aaa"),
            "bbb" -> js("bbb")),
          "arrObj" -> arr(Seq("aaa", "bbb", "ccc").map(v => obj("value" -> js(v))): _*))
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
        Mustache.render(template, json).get shouldBe expected
      }
    }
  }

  def js(s: Boolean): Json = Json.fromBoolean(s)

  def js(s: Int): Json = Json.fromInt(s)

  def js(s: Double): Json = Json.fromDoubleOrNull(s)

  def js(s: String): Json = Json.fromString(s)

  def arr(f: Json*): Json = Json.arr(f: _*)

  def obj(f: (String, Json)*): Json = Json.obj(f: _*)
}
