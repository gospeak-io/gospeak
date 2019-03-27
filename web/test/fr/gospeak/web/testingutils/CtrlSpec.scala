package fr.gospeak.web.testingutils

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.{Environment, LoginInfo, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import fr.gospeak.core.domain.User
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import org.scalatest.{FunSpec, Matchers}
import play.api.mvc.{AnyContent, BodyParsers}
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global

trait CtrlSpec extends FunSpec with Matchers with RandomDataGenerator {
  private val user: User = random[User]
  private val loginInfo: LoginInfo = LoginInfo(CredentialsProvider.ID, user.email.value)
  private val identity: AuthUser = AuthUser(loginInfo, user)

  private val playBodyParsers = Values.cc.parsers
  private val messagesApi = Values.cc.messagesApi
  private val bodyParsers: BodyParsers.Default = new BodyParsers.Default(playBodyParsers)
  private val env: Environment[CookieEnv] = FakeEnvironment[CookieEnv](Seq(identity.loginInfo -> identity))
  private val securedAction: SecuredAction = new DefaultSecuredAction(new DefaultSecuredRequestHandler(new DefaultSecuredErrorHandler(messagesApi)), bodyParsers)
  private val unsecuredAction: UnsecuredAction = new DefaultUnsecuredAction(new DefaultUnsecuredRequestHandler(new DefaultUnsecuredErrorHandler(messagesApi)), bodyParsers)
  private val userAwareAction: UserAwareAction = new DefaultUserAwareAction(new DefaultUserAwareRequestHandler(), bodyParsers)
  protected val silhouette: Silhouette[CookieEnv] = new SilhouetteProvider(env, securedAction, unsecuredAction, userAwareAction)
  protected val securedReq: FakeRequest[AnyContent] = FakeRequest().withAuthenticator(identity.loginInfo)(env)
  protected val unsecuredReq: FakeRequest[AnyContent] = FakeRequest()
}
