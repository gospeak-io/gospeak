package fr.gospeak.web.api.swagger

import fr.gospeak.web.services.openapi.OpenApiFactory
import org.scalatest.{FunSpec, Matchers}

import scala.util.Success

class SwaggerCtrlSpec extends FunSpec with Matchers {
  describe("SwaggerCtrl") {
    it("should load and parse the spec") {
      SwaggerCtrl.loadSpec().flatMap(OpenApiFactory.parseJson(_).toTry) shouldBe a[Success[_]]
    }
    /* it("should read route file") {
      val routes = RoutesUtils.loadRoutes().get
      routes.foreach(println)
      println(s"${routes.length} routes")
    } */
  }
}
