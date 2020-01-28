package gospeak.web.utils

import gospeak.web.utils.RoutesUtils._
import gospeak.libs.scala.FileUtils

import scala.util.Try

final case class Routes(routes: Seq[Route])

final case class Route(line: Int, verb: String, path: String, action: String) {
  def variables: Seq[String] =
    variableRegex.findAllIn(path).toList.map(v => v.head match {
      case ':' => v.stripPrefix(":")
      case '*' => v.stripPrefix("*")
      case '$' => v.stripPrefix("$").split("<").head
    })

  def mapVariables(f: String => String): Route =
    variables.foldLeft(this) { (route, variable) =>
      val p = route.path
        .replaceFirst(s":$variable", f(variable))
        .replaceFirst(s"\\*$variable", f(variable))
        .replaceFirst(s"\\$$$variable<[^>]+>", f(variable))
      route.copy(path = p)
    }
}

object RoutesUtils {
  // see https://www.playframework.com/documentation/2.8.x/ScalaRouting
  private val routeRegex = "(GET|POST|PUT|DELETE|PATCH|HEAD) +(/[^ ]*) +([^ ]+.*)".r
  private[utils] val variableRegex = "(:[^/]+)|(\\*.*)|(\\$[^<]+<[^>]+>)".r
  val routesPath = "conf/routes"

  def loadRoutes(): Try[Routes] = readFile().map(parseRoutes).map(_.collect { case Right(route) => route }).map(Routes)

  // current folder is "gospeak" in "sbt run" but "gospeak/web" in "sbt test"
  private[utils] def readFile(): Try[String] =
    if (FileUtils.exists(routesPath)) FileUtils.read(routesPath) else FileUtils.read(s"web/$routesPath")

  private[utils] def parseRoutes(routes: String): Seq[Either[(Int, String), Route]] =
    routes.split("\n").zipWithIndex.map { case (route, i) => parseRoute(i + 1, route) }.toList

  private[utils] def parseRoute(line: Int, route: String): Either[(Int, String), Route] = route match {
    case routeRegex(verb, path, action) => Right(Route(line, verb, path, action))
    case _ => Left(line -> route)
  }
}
