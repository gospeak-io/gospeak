package fr.gospeak.web.auth.exceptions

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import gospeak.libs.scala.domain.EmailAddress

case class DuplicateEmailException(email: EmailAddress) extends ProviderException(s"User email ${email.value} already exists")
