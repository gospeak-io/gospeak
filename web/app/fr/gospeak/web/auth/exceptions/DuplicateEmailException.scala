package fr.gospeak.web.auth.exceptions

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import fr.gospeak.libs.scalautils.domain.EmailAddress

case class DuplicateEmailException(email: EmailAddress) extends ProviderException(s"User email ${email.value} already exists")
