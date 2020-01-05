package fr.gospeak.web.services.openapi.models

import fr.gospeak.web.services.openapi.models.utils.{Js, TODO}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#media-type-object"
 */
final case class MediaType(schema: Option[Schema],
                           example: Option[Js],
                           examples: Option[Map[String, TODO]],
                           encoding: Option[TODO],
                           extensions: Option[TODO])
