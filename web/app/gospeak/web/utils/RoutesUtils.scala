package gospeak.web.utils

import gospeak.libs.scala.FileUtils
import gospeak.web.utils.RoutesUtils._

import scala.util.Try

final case class Routes(routes: List[Route])

final case class Route(line: Int, verb: String, path: String, action: String) {
  def variables: List[String] =
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

  def loadRoutes(): Try[Routes] = readFile().map(parseRoutes).map(_.collect { case Right(route) => route }).map(Routes)

  // current folder is "gospeak" in "sbt run" but "gospeak/web" in "sbt test"
  private[utils] def readFile(): Try[String] =
    FileUtils.read(FileUtils.adaptLocalPath("web/conf/routes"))

  private[utils] def parseRoutes(routes: String): List[Either[(Int, String), Route]] =
    routes.split("\n").zipWithIndex.map { case (route, i) => parseRoute(i + 1, route) }.toList

  private[utils] def parseRoute(line: Int, route: String): Either[(Int, String), Route] = route match {
    case routeRegex(verb, path, action) => Right(Route(line, verb, path, action))
    case _ => Left(line -> route)
  }
}
