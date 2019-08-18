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
import fr.gospeak.core.domain.utils.GospeakMessage
import fr.gospeak.core.services._
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.slack.SlackSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.libs.meetup.MeetupClient
import fr.gospeak.infra.libs.slack.SlackClient
import fr.gospeak.infra.services.meetup.MeetupSrvImpl
import fr.gospeak.infra.services.slack.SlackSrvImpl
import fr.gospeak.infra.services.storage.sql._
import fr.gospeak.infra.services.{EmailSrv, GravatarSrv, TemplateSrv}
import fr.gospeak.libs.scalautils.{BasicMessageBus, MessageBus}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.services.{AuthRepo, AuthSrv, CustomSecuredErrorHandler, CustomUnsecuredErrorHandler}
import fr.gospeak.web.auth.{AuthConf, AuthCtrl}
import fr.gospeak.web.domain.{GospeakMessageBus, MessageBuilder}
import fr.gospeak.web.pages._
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.speaker.talks.TalkCtrl
import fr.gospeak.web.pages.user.UserCtrl
import org.slf4j.LoggerFactory
import play.api.libs.ws.ahc.AhcWSComponents
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
    with AhcWSComponents
    with HttpFiltersComponents
    with _root_.controllers.AssetsComponents {
  private val logger = LoggerFactory.getLogger(this.getClass)
  logger.info("Start application")
  lazy val conf: AppConf = AppConf.load(configuration).get
  logger.info(s"Configuration loaded: $conf")
  lazy val envConf: ApplicationConf.Env = conf.application.env
  lazy val authConf: AuthConf = conf.auth
  lazy val dbConf: DatabaseConf = conf.database
  lazy val meetupConf: MeetupClient.Conf = conf.meetup

  lazy val db: GospeakDbSql = wire[GospeakDbSql]
  lazy val userRepo: UserRepo = db.user
  lazy val userRequestRepo: UserRequestRepo = db.userRequest
  lazy val groupRepo: GroupRepo = db.group
  lazy val eventRepo: EventRepo = db.event
  lazy val cfpRepo: CfpRepo = db.cfp
  lazy val talkRepo: TalkRepo = db.talk
  lazy val proposalRepo: ProposalRepo = db.proposal
  lazy val partnerRepo: PartnerRepo = db.partner
  lazy val venueRepo: VenueRepo = db.venue
  lazy val sponsorPackRepo: SponsorPackRepo = db.sponsorPack
  lazy val sponsorRepo: SponsorRepo = db.sponsor
  lazy val contactRepo: ContactRepo = db.contact
  lazy val settingsRepo: SettingsRepo = db.settings
  lazy val authRepo: AuthRepo = wire[AuthRepo]

  lazy val templateSrv: TemplateSrv = wire[TemplateSrv]
  lazy val gravatarSrv: GravatarSrv = wire[GravatarSrv]
  lazy val emailSrv: EmailSrv = EmailSrv.from(conf.emailService)
  lazy val meetupClient: MeetupClient = wire[MeetupClient]
  lazy val meetupSrv: MeetupSrv = wire[MeetupSrvImpl]
  lazy val slackClient: SlackClient = wire[SlackClient]
  lazy val slackSrv: SlackSrv = wire[SlackSrvImpl]
  lazy val messageBuilder: MessageBuilder = wire[MessageBuilder]
  lazy val messageBus: MessageBus[GospeakMessage] = wire[BasicMessageBus[GospeakMessage]]
  lazy val orgaMessageBus: GospeakMessageBus = wire[GospeakMessageBus]
  lazy val messageHandler: MessageHandler = wire[MessageHandler]
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
    new CookieAuthenticatorService(conf.auth.cookie.authenticator.toConf, None, signer, cookieHeaderEncoding, authenticatorEncoder, fingerprintGenerator, idGenerator, clock)
  }

  // lazy val socialProviderRegistry = SocialProviderRegistry(List())

  lazy val eventBus: EventBus = new EventBus()
  lazy val bodyParsers: BodyParsers.Default = new BodyParsers.Default(playBodyParsers)

  // lazy val silhouette: Silhouette[JwtEnv] = wire[SilhouetteProvider[JwtEnv]]
  lazy val silhouette: Silhouette[CookieEnv] = {
    val env: Environment[CookieEnv] = Environment[CookieEnv](authRepo, cookieAuth, List(), eventBus)

    val securedErrorHandler: SecuredErrorHandler = new CustomSecuredErrorHandler(messagesApi)
    val unsecuredErrorHandler: UnsecuredErrorHandler = new CustomUnsecuredErrorHandler(messagesApi)

    val securedRequestHandler: SecuredRequestHandler = new DefaultSecuredRequestHandler(securedErrorHandler)
    val unsecuredRequestHandler: UnsecuredRequestHandler = new DefaultUnsecuredRequestHandler(unsecuredErrorHandler)
    val userAwareRequestHandler: UserAwareRequestHandler = new DefaultUserAwareRequestHandler()

    val securedAction: SecuredAction = new DefaultSecuredAction(securedRequestHandler, bodyParsers)
    val unsecuredAction: UnsecuredAction = new DefaultUnsecuredAction(unsecuredRequestHandler, bodyParsers)
    val userAwareAction: UserAwareAction = new DefaultUserAwareAction(userAwareRequestHandler, bodyParsers)

    new SilhouetteProvider[CookieEnv](env, securedAction, unsecuredAction, userAwareAction)
  }
  // end:Silhouette conf

  lazy val authSrv: AuthSrv = AuthSrv(authConf, silhouette, userRepo, userRequestRepo, groupRepo, authRepo, clock, gravatarSrv)

  lazy val homeCtrl = wire[HomeCtrl]
  lazy val cfpCtrl = wire[published.cfps.CfpCtrl]
  lazy val groupCtrl = wire[published.groups.GroupCtrl]
  lazy val speakerCtrl = wire[published.speakers.SpeakerCtrl]
  lazy val authCtrl = wire[AuthCtrl]
  lazy val userCtrl = wire[UserCtrl]
  lazy val userGroupCtrl = wire[orga.GroupCtrl]
  lazy val userGroupEventCtrl = wire[orga.events.EventCtrl]
  lazy val userGroupCfpCtrl = wire[orga.cfps.CfpCtrl]
  lazy val userGroupCfpProposalCtrl = wire[orga.cfps.proposals.ProposalCtrl]
  lazy val userGroupProposalCtrl = wire[orga.proposals.ProposalCtrl]
  lazy val userGroupSpeakerCtrl = wire[orga.speakers.SpeakerCtrl]
  lazy val userGroupPartnerCtrl = wire[orga.partners.PartnerCtrl]
  lazy val userGroupPartnersVenueCtrl = wire[orga.partners.venues.VenueCtrl]
  lazy val userGroupVenueCtrl = wire[orga.venues.VenueCtrl]
  lazy val userGroupSponsorCtrl = wire[orga.sponsors.SponsorCtrl]
  lazy val userGroupSettingsCtrl = wire[orga.settings.SettingsCtrl]
  lazy val userSpeakerCtrl = wire[speaker.SpeakerCtrl]
  lazy val userTalkCtrl = wire[TalkCtrl]
  lazy val userTalkCfpCtrl = wire[fr.gospeak.web.pages.speaker.talks.cfps.CfpCtrl]
  lazy val userTalkProposalCtrl = wire[fr.gospeak.web.pages.speaker.talks.proposals.ProposalCtrl]
  lazy val apiStatusCtrl = wire[api.StatusCtrl]
  lazy val apiUiSuggestCtrl = wire[api.ui.SuggestCtrl]
  lazy val apiUiUtilsCtrl = wire[api.ui.UtilsCtrl]
  lazy val contactCtrl = wire[orga.partners.contacts.ContactCtrl]

  override lazy val router: Router = {
    val prefix = "/"
    wire[Routes]
  }

  def onStart(): Unit = {
    if (!envConf.isProd) {
      db.dropTables().unsafeRunSync() // TODO remove it for prod
    }
    db.migrate().unsafeRunSync()
    if (!envConf.isProd) {
      db.insertMockData().unsafeRunSync() // TODO remove it for prod
      // db.insertHTData(configuration.get[String]("mongo")).unsafeRunSync() // TODO remove it for prod
    }

    messageBus.subscribe(messageHandler.handle)

    logger.info("Application initialized")
  }

  onStart()
}
