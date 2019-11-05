package fr.gospeak.web.auth

import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorSettings
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.CookieSecretSettings
import com.mohiva.play.silhouette.impl.providers.{OAuth1Settings, OAuth2Settings}
import fr.gospeak.web.auth.AuthConf.CookieConf
import play.api.mvc.Cookie

import scala.concurrent.duration._

final case class AuthConf(cookie: CookieConf,
                          google: OAuth2Settings,
                          linkedIn: OAuth2Settings,
                          facebook: OAuth2Settings,
                          twitter: OAuth1Settings,
                          github: OAuth2Settings)

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

    def toCookieSecretSettings: CookieSecretSettings =
      CookieSecretSettings(cookieName, cookiePath, None, secureCookie, httpOnlyCookie, sameSite, authenticatorExpiry)
  }

  final case class RememberMe(cookieMaxAge: FiniteDuration,
                              authenticatorIdleTimeout: FiniteDuration,
                              authenticatorExpiry: FiniteDuration)


}


