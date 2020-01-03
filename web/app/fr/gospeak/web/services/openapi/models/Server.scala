package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage
import fr.gospeak.web.services.openapi.models.Server.Variable
import fr.gospeak.web.services.openapi.models.utils.{Markdown, TODO, Url}

/**
 * The @url attribute support variable substitutions using the @variables attribute
 *
 * @see "https://spec.openapis.org/oas/v3.0.2#server-object"
 */
final case class Server(url: Url,
                        description: Option[Markdown],
                        variables: Option[Map[String, Variable]],
                        extensions: Option[TODO]) {
  def validate: Option[Seq[ErrorMessage]] = {
    val errs = Server.extractVariables(url)
      .filterNot(v => variables.exists(_.contains(v)))
      .map(ErrorMessage.missingVariable)
    if (errs.isEmpty) None else Some(errs)
  }
}

object Server {

  /**
   * @see "https://spec.openapis.org/oas/v3.0.2#server-variable-object"
   */
  final case class Variable(default: String,
                            enum: Option[Seq[String]],
                            description: Option[Markdown])

  private val variableRegex = "\\{[^}]+}".r

  def extractVariables(url: Url): Seq[String] =
    variableRegex.findAllIn(url.value).toList.map(_.stripPrefix("{").stripSuffix("}"))
}
