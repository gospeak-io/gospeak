package fr.gospeak.web.testingutils

import java.util.Locale

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import com.mohiva.play.silhouette.api.{LoginInfo, Environment => SilhouetteEnvironment}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.test._
import fr.gospeak.core.domain.User
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.domain.Breadcrumb
import org.scalatest.{FunSpec, Matchers}
import play.api.i18n.{Lang, Messages}
import play.api.mvc.{AnyContent, Request}
import play.api.test.{CSRFTokenHelper, FakeRequest}

import scala.concurrent.ExecutionContext.Implicits.global

trait TwirlSpec extends FunSpec with Matchers with RandomDataGenerator {
  private val user: User = random[User]
  private val loginInfo: LoginInfo = AuthSrv.loginInfo(user.email)
  private val identity: AuthUser = AuthUser(loginInfo, user, Seq())

  private val env: SilhouetteEnvironment[CookieEnv] = FakeEnvironment[CookieEnv](Seq(identity.loginInfo -> identity))
  private val req: Request[AnyContent] = CSRFTokenHelper.addCSRFToken(FakeRequest().withAuthenticator(identity.loginInfo)(env))
  private val authenticator: CookieAuthenticator = FakeAuthenticator(loginInfo)(env, req)
  protected implicit val userAwareReq: UserAwareRequest[CookieEnv, AnyContent] = UserAwareRequest[CookieEnv, AnyContent](Some(identity), Some(authenticator), req)
  protected implicit val securedReq: SecuredRequest[CookieEnv, AnyContent] = SecuredRequest[CookieEnv, AnyContent](identity, authenticator, req)

  // private val env = Environment.simple()
  // private val context = ApplicationLoader.Context.create(env)
  // private val loader = new GospeakApplicationLoader()
  // private val application = loader.load(context)

  protected implicit val messages: Messages = new Messages {
    override def lang: Lang = Lang(Locale.ENGLISH)

    override def apply(key: String, args: Any*): String = key

    override def apply(keys: Seq[String], args: Any*): String = keys.headOption.getOrElse("")

    override def translate(key: String, args: Seq[Any]): Option[String] = Some(key)

    override def isDefinedAt(key: String): Boolean = true

    // override def asJava: JavaMessages = null
  }
  protected val b = Breadcrumb(Seq())
}
