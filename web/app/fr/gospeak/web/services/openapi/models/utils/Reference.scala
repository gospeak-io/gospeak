package fr.gospeak.web.services.openapi.models.utils

import cats.data.NonEmptyList
import fr.gospeak.web.services.openapi.error.OpenApiError.ErrorMessage

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

  def from(value: String): Either[NonEmptyList[ErrorMessage], Reference] = value match {
    case referenceObjectRegex(component, name) => Right(Reference(s"#/components/$component/$name"))
    case relativeDocumentRegex(relUrl) => Right(Reference(relUrl))
    case relativeDocumentWithEmbeddedSchemaRegex(relUrl, name) => Right(Reference(s"$relUrl#/$name"))
    case _ => Left(NonEmptyList.of(ErrorMessage.badFormat(value, "Reference", "#/components/.../...")))
  }

  def schema(name: String): Reference = Reference(s"#/components/schemas/$name")
}
