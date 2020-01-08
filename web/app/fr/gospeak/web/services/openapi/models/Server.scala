package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.error.OpenApiError
import fr.gospeak.web.services.openapi.models.Server.Variable
import fr.gospeak.web.services.openapi.models.utils.{HasValidation, Markdown, TODO, Url}

/**
 * The @url attribute support variable substitutions using the @variables attribute
 *
 * @see "https://spec.openapis.org/oas/v3.0.2#server-object"
 */
final case class Server(url: Url,
                        description: Option[Markdown],
                        variables: Option[Map[String, Variable]],
                        extensions: Option[TODO]) extends HasValidation {
  def expandedUrl: Url =
    Server.extractVariables(url).foldLeft(url) { (cur, key) =>
      variables.flatMap(_.get(key))
        .map(v => Url(cur.value.replace(s"{$key}", v.default)))
        .getOrElse(cur)
    }

  def getErrors(s: Schemas): List[OpenApiError] =
    Server.extractVariables(url)
      .filterNot(v => variables.exists(_.contains(v)))
      .map(OpenApiError.missingVariable(_).atPath(".url"))
}

object Server {

  /**
   * @see "https://spec.openapis.org/oas/v3.0.2#server-variable-object"
   */
  final case class Variable(default: String,
                            enum: Option[List[String]],
                            description: Option[Markdown],
                            extensions: Option[TODO])

  private val variableRegex = "\\{[^}]+}".r

  def extractVariables(url: Url): List[String] =
    variableRegex.findAllIn(url.value).toList.map(_.stripPrefix("{").stripSuffix("}"))
}
