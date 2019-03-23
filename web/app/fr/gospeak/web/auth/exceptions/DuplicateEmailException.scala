package fr.gospeak.web.auth.exceptions

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import fr.gospeak.libs.scalautils.domain.Email

case class DuplicateEmailException(email: Email) extends ProviderException(s"User email ${email.value} already exists")
