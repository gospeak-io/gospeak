package fr.gospeak.web

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.crypto.{Base64AuthenticatorEncoder, Crypter, CrypterAuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, FingerprintGenerator}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaSigner}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import com.softwaremill.macwire.wire
import fr.gospeak.infra.services.storage.sql.{DbSqlConf, GospeakDbSql, H2}
import fr.gospeak.infra.services.{ConsoleEmailSrv, EmailSrv}
import fr.gospeak.web.auth.AuthCtrl
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.services.{AuthRepo, AuthSrv}
import fr.gospeak.web.cfps.CfpCtrl
import fr.gospeak.web.domain.{AppConf, AuthCookieConf}
import fr.gospeak.web.groups.GroupCtrl
import fr.gospeak.web.speakers.SpeakerCtrl
import fr.gospeak.web.user.UserCtrl
import play.api.mvc.{BodyParsers, CookieHeaderEncoding, DefaultCookieHeaderEncoding}
import play.api.routing.Router
import play.api.{Environment => _, _}
import play.filters.HttpFiltersComponents
import router.Routes

import scala.concurrent.duration.{Duration, FiniteDuration}

class GospeakApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new GospeakComponents(context).application
  }
}

// use wire[] only for application classes (be explicit with others as they will not change)
class GospeakComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with _root_.controllers.AssetsComponents {
  lazy val conf: AppConf = AppConf.load(configuration).get
  lazy val cookieConf: AuthCookieConf = conf.auth.cookie

  lazy val dbConf: DbSqlConf = H2("org.h2.Driver", "jdbc:h2:mem:gospeak_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  lazy val db: GospeakDbSql = wire[GospeakDbSql]
  lazy val authRepo: AuthRepo = wire[AuthRepo]
  lazy val emailSrv: EmailSrv = wire[ConsoleEmailSrv]

  // start:Silhouette conf
  lazy val clock: Clock = Clock()
  lazy val idGenerator: SecureRandomIDGenerator = new SecureRandomIDGenerator()

  lazy val jwtAuth: AuthenticatorService[JWTAuthenticator] = {
    lazy val authenticatorDecoder: Base64AuthenticatorEncoder = new Base64AuthenticatorEncoder()
    val duration = configuration.underlying.getString("silhouette.jwt.authenticator.authenticatorExpiry")
    val expiration = Duration.apply(duration).asInstanceOf[FiniteDuration]
    val config = JWTAuthenticatorSettings(fieldName = configuration.underlying.getString("silhouette.jwt.authenticator.headerName"),
      issuerClaim = configuration.underlying.getString("silhouette.jwt.authenticator.issuerClaim"),
      authenticatorExpiry = expiration,
      sharedSecret = configuration.underlying.getString("silhouette.jwt.authenticator.sharedSecret"))
    new JWTAuthenticatorService(config, None, authenticatorDecoder, idGenerator, clock)
  }
  lazy val cookieAuth: AuthenticatorService[CookieAuthenticator] = {
    val signer: Signer = new JcaSigner(conf.auth.cookie.signer)
    val crypter: Crypter = new JcaCrypter(conf.auth.cookie.crypter)
    val authenticatorEncoder = new CrypterAuthenticatorEncoder(crypter)
    val cookieHeaderEncoding: CookieHeaderEncoding = new DefaultCookieHeaderEncoding()
    val fingerprintGenerator: FingerprintGenerator = new DefaultFingerprintGenerator(false)
    new CookieAuthenticatorService(conf.auth.cookie.authenticator, None, signer, cookieHeaderEncoding, authenticatorEncoder, fingerprintGenerator, idGenerator, clock)
  }

  // lazy val socialProviderRegistry = SocialProviderRegistry(List())

  lazy val eventBus: EventBus = new EventBus()
  lazy val bodyParsers: BodyParsers.Default = new BodyParsers.Default(playBodyParsers)

  // lazy val silhouette: Silhouette[JwtEnv] = wire[SilhouetteProvider[JwtEnv]]
  lazy val silhouette: Silhouette[CookieEnv] = {
    val env: Environment[CookieEnv] = Environment[CookieEnv](authRepo, cookieAuth, List(), eventBus)

    val securedErrorHandler: SecuredErrorHandler = new DefaultSecuredErrorHandler(messagesApi)
    val unsecuredErrorHandler: UnsecuredErrorHandler = new DefaultUnsecuredErrorHandler(messagesApi)

    val securedRequestHandler: SecuredRequestHandler = new DefaultSecuredRequestHandler(securedErrorHandler)
    val unsecuredRequestHandler: UnsecuredRequestHandler = new DefaultUnsecuredRequestHandler(unsecuredErrorHandler)
    val userAwareRequestHandler: UserAwareRequestHandler = new DefaultUserAwareRequestHandler()

    val securedAction: SecuredAction = new DefaultSecuredAction(securedRequestHandler, bodyParsers)
    val unsecuredAction: UnsecuredAction = new DefaultUnsecuredAction(unsecuredRequestHandler, bodyParsers)
    val userAwareAction: UserAwareAction = new DefaultUserAwareAction(userAwareRequestHandler, bodyParsers)

    new SilhouetteProvider[CookieEnv](env, securedAction, unsecuredAction, userAwareAction)
  }
  // end:Silhouette conf

  lazy val authSrv: AuthSrv = AuthSrv(cookieConf, silhouette, db, authRepo, clock)

  lazy val homeCtrl = wire[HomeCtrl]
  lazy val cfpCtrl = wire[CfpCtrl]
  lazy val groupCtrl = wire[GroupCtrl]
  lazy val speakerCtrl = wire[SpeakerCtrl]
  lazy val authCtrl = wire[AuthCtrl]
  lazy val userCtrl = wire[UserCtrl]
  lazy val userGroupCtrl = wire[user.groups.GroupCtrl]
  lazy val userGroupEventCtrl = wire[user.groups.events.EventCtrl]
  lazy val userGroupProposalCtrl = wire[user.groups.proposals.ProposalCtrl]
  lazy val userGroupProposalSpeakerCtrl = wire[user.groups.proposals.speakers.SpeakerCtrl]
  lazy val userGroupSettingsCtrl = wire[user.groups.settings.SettingsCtrl]
  lazy val userTalkCtrl = wire[user.talks.TalkCtrl]
  lazy val userTalkCfpCtrl = wire[user.talks.cfps.CfpCtrl]
  lazy val userTalkProposalCtrl = wire[user.talks.proposals.ProposalCtrl]
  lazy val apiUiUtilsCtrl = wire[api.ui.UtilsCtrl]

  override lazy val router: Router = {
    val prefix = "/"
    wire[Routes]
  }

  def onStart(): Unit = {
    println("Starting application")
    db.dropTables().unsafeRunSync()
    db.createTables().unsafeRunSync()
    db.insertMockData().unsafeRunSync()
  }

  onStart()
}
