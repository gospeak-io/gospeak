package gospeak.web.api.swagger

import gospeak.web.services.openapi.OpenApiFactory
import gospeak.web.utils.{JsonUtils, OpenApiUtils, RoutesUtils}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json._

class SwaggerCtrlSpec extends FunSpec with Matchers {
  describe("SwaggerCtrl") {
    it("should load, parse and serialize the spec") {
      // "X-vars" is an extension, it's not handled now :(
      val json = OpenApiUtils.loadSpec().get.transform((__ \ "X-vars").json.prune).get
      val spec = OpenApiFactory.parseJson(json).toTry.get
      val serialized = OpenApiFactory.toJson(spec)
      JsonUtils.diff(json, serialized) shouldBe Seq()
      serialized shouldBe json
    }
    it("should document every /api route") {
      val routes = RoutesUtils.loadRoutes().get
      val doc = OpenApiUtils.loadSpec().flatMap(OpenApiFactory.parseJson(_).toTry).get

      val cleanPaths = doc.paths.map { case (path, _) => path.mapVariables(_ => "?").value }.toSet
      val failed = routes.routes.filter(r => r.path.startsWith("/api/") && r.path != "/api/openapi.json").filterNot { route =>
        cleanPaths.contains(route.mapVariables(_ => "?").path.stripPrefix("/api"))
      }

      if (failed.nonEmpty) {
        fail(s"Some /api routes are not documented in web/${OpenApiUtils.specPath}:${failed.map("\n  - " + _.path).mkString}")
      }
    }
  }
}
