package gospeak.libs.scala.domain

import gospeak.libs.scala.domain.LiquidSpec.User
import gospeak.libs.testingutils.BaseSpec
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, Json}

class LiquidSpec extends BaseSpec {
  describe("Liquid") {
    it("should render a basic template") {
      Liquid.render("Hello {{name}}", Json.obj("name" -> Json.fromString("world"))) shouldBe Right("Hello world")
    }
    it("should render for and if") {
      val tmpl =
        """Users:
          |<ul>
          |  {%- for user in users -%}
          |    {%- if user.name != "Claude" -%}
          |      <li>{{user.name}}</li>
          |    {%- endif -%}
          |  {%- else -%}
          |    <li>No users</li>
          |  {%- endfor -%}
          |</ul>
          |""".stripMargin

      Liquid.render(tmpl, Json.obj(
        "users" -> Json.arr(
          Json.obj("name" -> Json.fromString("Jean")),
          Json.obj("name" -> Json.fromString("Claude"))))) shouldBe Right(
        """Users:
          |<ul><li>Jean</li></ul>
          |""".stripMargin)

      Liquid.render(tmpl, Json.obj(
        "users" -> Json.arr(
          Json.obj("name" -> Json.fromString("Jean")),
          Json.obj("name" -> Json.fromString("Luc"))))) shouldBe Right(
        """Users:
          |<ul><li>Jean</li><li>Luc</li></ul>
          |""".stripMargin)

      Liquid.render(tmpl, Json.obj("users" -> Json.arr())) shouldBe Right(
        """Users:
          |<ul><li>No users</li></ul>
          |""".stripMargin)

      // Liquid.render(tmpl, Json.obj()) shouldBe Left(Liquid.Error.MissingVariable("users"))
    }
    it("should manage filters") {
      Liquid.render("{{ \"title\" | capitalize }}", Json.obj()) shouldBe Right("Title")
      Liquid.render("{{ \"2020-07-15 16:02:12\" | date: \"%A, %B %d, %Y %H:%M\" }}", Json.obj()) shouldBe Right("Wednesday, July 15, 2020 16:02")
      Liquid.render("{{ \"2020-07-15 16:02:12\" | date: \"%d-%m-%Y\" }}", Json.obj()) shouldBe Right("15-07-2020")
      Liquid.render("{{ 1594821722 | date: \"%d-%m-%Y\" }}", Json.obj()) shouldBe Right("15-07-2020")
    }
    it("should use circe encoding") {
      Liquid[User]("Hello {{name}}").render(User("Loic")) shouldBe Right("Hello Loic")
      LiquidHtml[User]("Hello {{name}}").render(User("Loic")) shouldBe Right(Html("Hello Loic"))
      LiquidMarkdown[User]("Hello {{name}}").render(User("Loic")) shouldBe Right(Markdown("Hello Loic"))
    }
    /* it("should handle missing values") {
      Liquid.render("Hello {{name}}", Json.obj()) shouldBe Left(Liquid.Error.MissingVariable("name"))
      Liquid.render("Hello {{user}}, are you {{name}}?", Json.obj()) shouldBe Left(Liquid.Error.MissingVariable("user"))
    } */
    it("should handle bad template") {
      Liquid.render("Hello {{name}", Json.obj()) shouldBe Left(Liquid.Error.InvalidTemplate(1, 13, "<EOF>", List("OutEnd", "Pipe")))
      Liquid.render("Hello {% if 'a' === 1 %}{% endif %}", Json.obj()) shouldBe Left(Liquid.Error.LiquidError(1, 18, "parser error \"extraneous input '=' expecting {Str, '(', '[', DoubleNum, LongNum, 'capture', 'endcapture', 'comment', 'endcomment', RawStart, 'if', 'elsif', 'endif', 'unless', 'endunless', 'else', 'contains', 'case', 'endcase', 'when', 'cycle', 'for', 'endfor', 'in', 'and', 'or', 'tablerow', 'endtablerow', 'assign', 'true', 'false', Nil, 'include', 'with', 'empty', 'blank', EndId, Id, RawEnd}\" on line 1, index 18"))
      Liquid.render("Hello {% aa %}{% endaa %}", Json.obj()) shouldBe Left(Liquid.Error.Unknown("Unknown liquid error without message"))
    }
  }
}

object LiquidSpec {

  case class User(name: String)

  object User {
    implicit val encoder: Encoder[User] = deriveEncoder[User]
  }

}
