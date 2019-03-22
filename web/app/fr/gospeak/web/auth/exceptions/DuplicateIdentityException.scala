package fr.gospeak.web.auth.exceptions

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.exceptions.ProviderException

case class DuplicateIdentityException(loginInfo: LoginInfo) extends ProviderException(s"User ${loginInfo.providerKey} already exists") {
  override def getMessage: String = s"User ${loginInfo.providerKey} already exists"
}
