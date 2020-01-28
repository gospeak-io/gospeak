package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.error.OpenApiError

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#reference-object"
 */
final case class Reference(value: String) extends AnyVal {
  def localRef: Option[(String, String)] = value match {
    case Reference.referenceObjectRegex(component, name) => Some(component -> name)
    case _ => None
  }
}

object Reference {
  // https://spec.openapis.org/oas/v3.0.2#reference-object-example
  private val referenceObjectRegex = "#/components/([^/]+)/([^/]+)".r
  // https://spec.openapis.org/oas/v3.0.2#relative-schema-document-example
  private val relativeDocumentRegex = "([^#? ]+)".r // TODO: to improve
  // https://spec.openapis.org/oas/v3.0.2#relative-documents-with-embedded-schema-example
  private val relativeDocumentWithEmbeddedSchemaRegex = "([^#? ]+)#/([^/]+)".r // TODO: to improve

  def from(value: String): Either[OpenApiError, Reference] = value match {
    case referenceObjectRegex(component, name) => Right(Reference(s"#/components/$component/$name"))
    case relativeDocumentRegex(relUrl) => Right(Reference(relUrl))
    case relativeDocumentWithEmbeddedSchemaRegex(relUrl, name) => Right(Reference(s"$relUrl#/$name"))
    case _ => Left(OpenApiError.badFormat(value, "Reference", "#/components/.../..."))
  }

  def schema(name: String): Reference = Reference(s"#/components/schemas/$name")

  def parameter(name: String): Reference = Reference(s"#/components/parameters/$name")

  def requestBody(name: String): Reference = Reference(s"#/components/requestBodies/$name")

  def header(name: String): Reference = Reference(s"#/components/headers/$name")

  def response(name: String): Reference = Reference(s"#/components/responses/$name")

  def callback(name: String): Reference = Reference(s"#/components/callbacks/$name")

  def securityScheme(name: String): Reference = Reference(s"#/components/securitySchemes/$name")

  def link(name: String): Reference = Reference(s"#/components/links/$name")

  def example(name: String): Reference = Reference(s"#/components/examples/$name")
}
