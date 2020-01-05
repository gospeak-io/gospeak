package fr.gospeak.web.api.swagger

import fr.gospeak.web.services.openapi.OpenApiFactory
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json._

class SwaggerCtrlSpec extends FunSpec with Matchers {
  describe("SwaggerCtrl") {
    it("should load, parse and serialize the spec") {
      // "X-vars" is an extension, it's not handled now :(
      val json = SwaggerCtrl.loadSpec().get.transform((__ \ "X-vars").json.prune).get
      val spec = OpenApiFactory.parseJson(json).toTry.get
      val serialized = OpenApiFactory.toJson(spec)
      serialized shouldBe json
    }
    /* it("should read route file") {
      val routes = RoutesUtils.loadRoutes().get
      routes.foreach(println)
      println(s"${routes.length} routes")
    } */
  }
}
