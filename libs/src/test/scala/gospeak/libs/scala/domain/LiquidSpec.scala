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
    it("should handle bad template") {
      Liquid.render("Hello {{name}", Json.obj()) shouldBe Left(Liquid.Error.InvalidTemplate(1, 13, "<EOF>", List("OutEnd", "Pipe")))
    }
    it("should handle missing values") {
      Liquid.render("Hello {{name}}", Json.obj()) shouldBe Left(Liquid.Error.MissingVariable("name"))
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

      Liquid.render(tmpl, Json.obj()) shouldBe Left(Liquid.Error.MissingVariable("users"))
    }
    it("should manage filters") {
      Liquid.render("{{ \"title\" | capitalize }}", Json.obj()) shouldBe Right("Title")
    }
    it("should use circe encoding") {
      Liquid[User]("Hello {{name}}").render(User("Loic")) shouldBe Right("Hello Loic")
      LiquidHtml[User]("Hello {{name}}").render(User("Loic")) shouldBe Right(Html("Hello Loic"))
      LiquidMarkdown[User]("Hello {{name}}").render(User("Loic")) shouldBe Right(Markdown("Hello Loic"))
    }
  }
}

object LiquidSpec {

  case class User(name: String)

  object User {
    implicit val encoder: Encoder[User] = deriveEncoder[User]
  }

}
