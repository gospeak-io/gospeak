package fr.gospeak.web

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.crypto.{Base64AuthenticatorEncoder, Crypter, CrypterAuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, FingerprintGenerator, PasswordHasher, PasswordHasherRegistry}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaSigner}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers.{CredentialsProvider, SocialProviderRegistry}
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
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

class GospeakComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with _root_.controllers.AssetsComponents {
  lazy val conf: AppConf = AppConf.load(configuration).get
  lazy val cookieConf: AuthCookieConf = conf.auth.cookie

  lazy val dbConf: DbSqlConf = H2("org.h2.Driver", "jdbc:h2:mem:gospeak_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  lazy val emailSrv: EmailSrv = new ConsoleEmailSrv()
  lazy val db: GospeakDbSql = wire[GospeakDbSql]

  // start:Silhouette conf
  lazy val authRepo: AuthRepo = wire[AuthRepo]
  lazy val bodyParsers: BodyParsers.Default = wire[BodyParsers.Default]

  lazy val clock: Clock = wire[Clock]
  lazy val authenticatorDecoder: Base64AuthenticatorEncoder = wire[Base64AuthenticatorEncoder]
  lazy val idGenerator = new SecureRandomIDGenerator()
  lazy val eventBus: EventBus = wire[EventBus]

  lazy val jwtAuth: AuthenticatorService[JWTAuthenticator] = {
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

  private lazy val env: Environment[CookieEnv] = Environment[CookieEnv](authRepo, cookieAuth, List(), eventBus)

  lazy val securedErrorHandler: SecuredErrorHandler = wire[DefaultSecuredErrorHandler]
  lazy val unSecuredErrorHandler: UnsecuredErrorHandler = wire[DefaultUnsecuredErrorHandler]

  lazy val securedRequestHandler: SecuredRequestHandler = wire[DefaultSecuredRequestHandler]
  lazy val unsecuredRequestHandler: UnsecuredRequestHandler = wire[DefaultUnsecuredRequestHandler]
  lazy val userAwareRequestHandler: UserAwareRequestHandler = wire[DefaultUserAwareRequestHandler]

  lazy val securedAction: SecuredAction = wire[DefaultSecuredAction]
  lazy val unsecuredAction: UnsecuredAction = wire[DefaultUnsecuredAction]
  lazy val userAwareAction: UserAwareAction = wire[DefaultUserAwareAction]

  lazy val authInfoRepository = new DelegableAuthInfoRepository(authRepo)
  lazy val bCryptPasswordHasher: PasswordHasher = new BCryptPasswordHasher
  lazy val passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(bCryptPasswordHasher)

  lazy val credentialsProvider = new CredentialsProvider(authInfoRepository, passwordHasherRegistry)

  lazy val socialProviderRegistry = SocialProviderRegistry(List())

  // lazy val silhouette: Silhouette[JwtEnv] = wire[SilhouetteProvider[JwtEnv]]
  lazy val silhouette: Silhouette[CookieEnv] = wire[SilhouetteProvider[CookieEnv]]
  lazy val authSrv: AuthSrv = wire[AuthSrv]
  // end:Silhouette conf

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
