package fr.gospeak.web.auth

import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.crypto.{JcaCrypterSettings, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorSettings
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.{CookieSecretProvider, CookieSecretSettings}
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
import com.mohiva.play.silhouette.impl.providers.oauth2.{FacebookProvider, GitHubProvider, GoogleProvider, LinkedInProvider}
import com.mohiva.play.silhouette.impl.providers.state.CsrfStateSettings
import com.mohiva.play.silhouette.impl.providers.{OAuth1Settings, OAuth2Settings, SocialProvider, SocialStateHandler}
import fr.gospeak.web.auth.AuthConf._
import gospeak.libs.scala.domain.Secret
import play.api.mvc.Cookie

import scala.concurrent.duration._

final case class AuthConf(cookie: CookieConf,
                          facebook: FacebookConf,
                          github: GithubConf,
                          google: GoogleConf,
                          linkedin: LinkedinConf,
                          twitter: TwitterConf)

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

    def toCsrfStateSettings: CsrfStateSettings =
      CsrfStateSettings(cookieName, cookiePath, None, secureCookie, httpOnlyCookie, sameSite, authenticatorExpiry)
  }

  final case class RememberMe(cookieMaxAge: FiniteDuration,
                              authenticatorIdleTimeout: FiniteDuration,
                              authenticatorExpiry: FiniteDuration)

  sealed trait SocialConf {
    def toProvider(baseUrl: String, httpLayer: HTTPLayer, socialStateHandler: SocialStateHandler, cookieProvider: CookieSecretProvider): SocialProvider
  }

  final case class FacebookConf(clientId: String, clientSecret: Secret, scope: Option[String]) extends SocialConf {
    def toProvider(baseUrl: String, httpLayer: HTTPLayer, socialStateHandler: SocialStateHandler, cookieProvider: CookieSecretProvider): FacebookProvider = {
      val conf = OAuth2Settings(
        authorizationURL = Some("https://graph.facebook.com/v2.3/oauth/authorize"),
        accessTokenURL = "https://graph.facebook.com/v2.3/oauth/access_token",
        redirectURL = Some(s"${baseUrl.stripSuffix("/")}/authenticate/facebook"),
        clientID = clientId,
        clientSecret = clientSecret.decode,
        scope = scope)
      new FacebookProvider(httpLayer, socialStateHandler, conf)
    }
  }

  final case class GithubConf(clientId: String, clientSecret: Secret) extends SocialConf {
    def toProvider(baseUrl: String, httpLayer: HTTPLayer, socialStateHandler: SocialStateHandler, cookieProvider: CookieSecretProvider): GitHubProvider = {
      val conf = OAuth2Settings(
        authorizationURL = Some("https://github.com/login/oauth/authorize"),
        accessTokenURL = "https://github.com/login/oauth/access_token",
        redirectURL = Some(s"${baseUrl.stripSuffix("/")}/authenticate/github"),
        clientID = clientId,
        clientSecret = clientSecret.decode)
      new GitHubProvider(httpLayer, socialStateHandler, conf)
    }
  }

  final case class GoogleConf(clientId: String, clientSecret: Secret, scope: Option[String]) extends SocialConf {
    def toProvider(baseUrl: String, httpLayer: HTTPLayer, socialStateHandler: SocialStateHandler, cookieProvider: CookieSecretProvider): GoogleProvider = {
      val conf = OAuth2Settings(
        authorizationURL = Some("https://accounts.google.com/o/oauth2/auth"),
        accessTokenURL = "https://accounts.google.com/o/oauth2/token",
        redirectURL = Some(s"${baseUrl.stripSuffix("/")}/authenticate/google"),
        clientID = clientId,
        clientSecret = clientSecret.decode,
        scope = scope)
      new GoogleProvider(httpLayer, socialStateHandler, conf)
    }
  }

  final case class LinkedinConf(clientId: String, clientSecret: Secret, scope: Option[String]) extends SocialConf {
    def toProvider(baseUrl: String, httpLayer: HTTPLayer, socialStateHandler: SocialStateHandler, cookieProvider: CookieSecretProvider): LinkedInProvider = {
      val conf = OAuth2Settings(
        authorizationURL = Some("https://www.linkedin.com/oauth/v2/authorization"),
        accessTokenURL = "https://www.linkedin.com/oauth/v2/accessToken",
        redirectURL = Some(s"${baseUrl.stripSuffix("/")}/authenticate/linkedin"),
        clientID = clientId,
        clientSecret = clientSecret.decode,
        scope = scope)
      new LinkedInProvider(httpLayer, socialStateHandler, conf)
    }
  }

  final case class TwitterConf(consumerKey: String, consumerSecret: Secret) extends SocialConf {
    def toProvider(baseUrl: String, httpLayer: HTTPLayer, socialStateHandler: SocialStateHandler, cookieProvider: CookieSecretProvider): TwitterProvider = {
      val conf = OAuth1Settings(
        requestTokenURL = "https://twitter.com/oauth/request_token",
        accessTokenURL = "https://twitter.com/oauth/access_token",
        authorizationURL = "https://twitter.com/oauth/authenticate",
        callbackURL = s"${baseUrl.stripSuffix("/")}/authenticate/twitter",
        apiURL = None,
        consumerKey = consumerKey,
        consumerSecret = consumerSecret.decode)
      new TwitterProvider(httpLayer, new PlayOAuth1Service(conf), cookieProvider, conf)
    }
  }

}
