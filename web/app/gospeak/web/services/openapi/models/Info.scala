package gospeak.web.services.openapi.models

import gospeak.web.services.openapi.models.Info.{Contact, License}
import gospeak.web.services.openapi.models.utils.{Email, Markdown, TODO, Url, Version}

/**
 * @see "https://spec.openapis.org/oas/v3.0.2#info-object"
 */
final case class Info(title: String,
                      description: Option[Markdown],
                      termsOfService: Option[Url],
                      contact: Option[Contact],
                      license: Option[License],
                      version: Version,
                      extensions: Option[TODO])

object Info {

  /**
   * @see "https://spec.openapis.org/oas/v3.0.2#contact-object"
   */
  final case class Contact(name: Option[String],
                           url: Option[Url],
                           email: Option[Email],
                           extensions: Option[TODO])

  /**
   * @see "https://spec.openapis.org/oas/v3.0.2#license-object"
   */
  final case class License(name: String,
                           url: Option[Url],
                           extensions: Option[TODO])

}
