package fr.gospeak.web.auth

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorSettings

import scala.concurrent.duration.FiniteDuration

final case class AuthConf(cookie: AuthConf.CookieConf)

object AuthConf {

  final case class CookieConf(authenticator: CookieAuthenticatorSettings,
                              signer: JcaSignerSettings,
                              crypter: JcaCrypterSettings,
                              rememberMe: RememberMe)

  final case class RememberMe(cookieMaxAge: FiniteDuration,
                              authenticatorIdleTimeout: FiniteDuration,
                              authenticatorExpiry: FiniteDuration)

}
