package fr.gospeak.web.auth

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorSettings
import play.api.mvc.Cookie

import scala.concurrent.duration._

final case class AuthConf(cookie: AuthConf.CookieConf)

object AuthConf {

  final case class CookieConf(authenticator: CookieSettings,
                              signer: JcaSignerSettings,
                              crypter: JcaCrypterSettings,
                              rememberMe: RememberMe)

  final case class CookieSettings(cookieName: String,
                                  cookiePath: String,
                                  // cookieDomain: String,
                                  secureCookie: Boolean,
                                  httpOnlyCookie: Boolean,
                                  sameSite: Option[Cookie.SameSite],
                                  useFingerprinting: Boolean,
                                  // cookieMaxAge: FiniteDuration,
                                  authenticatorIdleTimeout: FiniteDuration,
                                  authenticatorExpiry: FiniteDuration) {
    def toConf: CookieAuthenticatorSettings =
      CookieAuthenticatorSettings(cookieName, cookiePath, None, secureCookie, httpOnlyCookie, sameSite, useFingerprinting, None, Some(authenticatorIdleTimeout), authenticatorExpiry)
  }

  final case class RememberMe(cookieMaxAge: FiniteDuration,
                              authenticatorIdleTimeout: FiniteDuration,
                              authenticatorExpiry: FiniteDuration)

}
