package fr.gospeak.web.api.swagger

import fr.gospeak.web.services.openapi.OpenApiFactory
import fr.gospeak.web.utils.{OpenApiUtils, RoutesUtils}
import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json._

class SwaggerCtrlSpec extends FunSpec with Matchers {
  describe("SwaggerCtrl") {
    it("should load, parse and serialize the spec") {
      // "X-vars" is an extension, it's not handled now :(
      val json = OpenApiUtils.loadSpec().get.transform((__ \ "X-vars").json.prune).get
      val spec = OpenApiFactory.parseJson(json).toTry.get
      val serialized = OpenApiFactory.toJson(spec)
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

  // quick & dirty diff to help troubleshooting
  private def diff(json1: JsValue, json2: JsValue): JsValue = {
    (json1, json2) match {
      case (JsNull, JsNull) => JsNull
      case (JsBoolean(b1), JsBoolean(b2)) => if (b1 == b2) JsNull else JsBoolean(b1)
      case (JsNumber(n1), JsNumber(n2)) => if (n1 == n2) JsNull else JsNumber(n1)
      case (JsString(s1), JsString(s2)) => if (s1 == s2) JsNull else JsString(s1)
      case (JsArray(a1), JsArray(a2)) =>
        val res = a1.zip(a2).map { case (e1, e2) => diff(e1, e2) }.filter(_ != JsNull) ++ a2.drop(a1.length)
        if (res.isEmpty) JsNull else JsArray(res)
      case (JsObject(o1), JsObject(o2)) =>
        val res = o1.map { case (k, v1) => k -> o2.get(k).map(v2 => diff(v1, v2)).getOrElse(v1) }.filter(_._2 != JsNull) ++ o2.filterKeys(!o1.contains(_))
        if (res.isEmpty) JsNull else JsObject(res)
      case (json, _) => json
    }
  }
}
