package gospeak.web.auth.exceptions

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import gospeak.web.auth.domain.AuthUser

case class AccountValidationRequiredException(identity: AuthUser) extends ProviderException(s"Account validation required")
