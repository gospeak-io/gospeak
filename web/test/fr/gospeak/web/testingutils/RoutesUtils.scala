package fr.gospeak.web.testingutils

import fr.gospeak.libs.scalautils.FileUtils
import org.scalatest.{FunSpec, Matchers}

import scala.util.Try

final case class Route(line: Int, verb: String, path: String, action: String)

object RoutesUtils {
  private val routeRegex = "(GET|POST|PUT|DELETE) +(/[^ ]*) +([^ ]+.*)".r

  def loadRoutes(): Try[Seq[Route]] = readFile().map(parseRoutes).map(_.collect { case Right(route) => route })

  private[testingutils] def readFile(): Try[String] = FileUtils.read("web/conf/routes")

  private[testingutils] def parseRoutes(routes: String): Seq[Either[(Int, String), Route]] =
    routes.split("\n").zipWithIndex.map { case (route, i) => parseRoute(i + 1, route) }.toList

  private[testingutils] def parseRoute(line: Int, route: String): Either[(Int, String), Route] = route match {
    case routeRegex(verb, path, action) => Right(Route(line, verb, path, action))
    case _ => Left(line -> route)
  }
}

class RoutesUtilsSpec extends FunSpec with Matchers {
  describe("RoutesUtils") {
    it("should parse simple routes") {
      RoutesUtils.parseRoute(1, "GET     /       gospeak.HomeCtrl.index") shouldBe Right(Route(1, "GET", "/", "gospeak.HomeCtrl.index"))
      RoutesUtils.parseRoute(2, "GET     /why    gospeak.HomeCtrl.why") shouldBe Right(Route(2, "GET", "/why", "gospeak.HomeCtrl.why"))
    }
  }
}
