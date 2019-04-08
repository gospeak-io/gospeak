package fr.gospeak.web.testingutils

import java.util.UUID

import akka.stream.Materializer
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.api.{Environment, LoginInfo, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.test._
import com.typesafe.config.ConfigFactory
import fr.gospeak.core.domain.User
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.infra.services.storage.sql.{DatabaseConf, GospeakDbSql}
import fr.gospeak.web.{AppConf, GospeakComponents}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.services.AuthSrv
import org.scalatest.{FunSpec, Matchers}
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.BuiltInComponents
import play.api.mvc._
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, Helpers, NoMaterializer}

import scala.concurrent.ExecutionContext.Implicits.global

trait CtrlSpec extends FunSpec with Matchers with RandomDataGenerator with OneAppPerSuiteWithComponents {
  override def components: BuiltInComponents = new GospeakComponents(context)

  // play
  protected val cc: ControllerComponents = Helpers.stubControllerComponents()
  private val playBodyParsers = cc.parsers
  private val messagesApi = cc.messagesApi
  private val bodyParsers: BodyParsers.Default = new BodyParsers.Default(playBodyParsers)

  // silhouette
  private val user: User = random[User]
  private val loginInfo: LoginInfo = AuthSrv.loginInfo(user.email)
  private val identity: AuthUser = AuthUser(loginInfo, user)
  protected val clock = Clock()
  private val env: Environment[CookieEnv] = FakeEnvironment[CookieEnv](Seq(identity.loginInfo -> identity))
  private val securedAction: SecuredAction = new DefaultSecuredAction(new DefaultSecuredRequestHandler(new DefaultSecuredErrorHandler(messagesApi)), bodyParsers)
  private val unsecuredAction: UnsecuredAction = new DefaultUnsecuredAction(new DefaultUnsecuredRequestHandler(new DefaultUnsecuredErrorHandler(messagesApi)), bodyParsers)
  private val userAwareAction: UserAwareAction = new DefaultUserAwareAction(new DefaultUserAwareRequestHandler(), bodyParsers)
  protected val silhouette: Silhouette[CookieEnv] = new SilhouetteProvider(env, securedAction, unsecuredAction, userAwareAction)
  protected val unsecuredReq: RequestHeader = FakeRequest().withCSRFToken
  protected val securedReq: RequestHeader = FakeRequest().withAuthenticator(identity.loginInfo)(env).withCSRFToken
  protected implicit val mat: Materializer = NoMaterializer

  // app
  protected val conf: AppConf = AppConf.load(ConfigFactory.load()).get
  protected val db: GospeakDbSql = new GospeakDbSql(DatabaseConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"))
}
