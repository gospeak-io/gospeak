package gospeak.web

import java.util.concurrent.TimeUnit

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.crypto.{Crypter, CrypterAuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, FingerprintGenerator, HTTPLayer, PlayHTTPLayer}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaSigner}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.CookieSecretProvider
import com.mohiva.play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
import com.mohiva.play.silhouette.impl.providers.{DefaultSocialStateHandler, SocialProviderRegistry}
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import com.softwaremill.macwire.wire
import gospeak.core.ApplicationConf
import gospeak.core.domain.messages.Message
import gospeak.core.services.cloudinary.UploadSrv
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.meetup.MeetupSrv
import gospeak.core.services.places.PlacesSrv
import gospeak.core.services.slack.SlackSrv
import gospeak.core.services.storage._
import gospeak.core.services.twitter.TwitterSrv
import gospeak.core.services.video.VideoSrv
import gospeak.infra.services.email.EmailSrvFactory
import gospeak.infra.services.meetup.MeetupSrvImpl
import gospeak.infra.services.places.PlacesSrvImpl
import gospeak.infra.services.slack.SlackSrvImpl
import gospeak.infra.services.storage.sql._
import gospeak.infra.services.twitter.TwitterSrvFactory
import gospeak.infra.services.upload.UploadSrvFactory
import gospeak.infra.services.video.VideoSrvImpl
import gospeak.infra.services.{AvatarSrv, EmbedSrv, ScraperSrv}
import gospeak.libs.http.{HttpClient, HttpClientImpl}
import gospeak.libs.scala.{BasicMessageBus, MessageBus}
import gospeak.libs.slack.SlackClient
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.auth.services.{AuthRepo, AuthSrv, CustomSecuredErrorHandler, CustomUnsecuredErrorHandler}
import gospeak.web.auth.{AuthConf, AuthCtrl}
import gospeak.web.pages._
import gospeak.web.services.{MessageHandler, MessageSrv, SchedulerSrv}
import org.slf4j.LoggerFactory
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Cookie.SameSite
import play.api.mvc.{BodyParsers, CookieHeaderEncoding, DefaultCookieHeaderEncoding}
import play.api.routing.Router
import play.api.{Environment => _, _}
import play.filters.HttpFiltersComponents
import router.Routes

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class GsApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new GsComponents(context).application
  }
}

// use wire[] only for application classes (be explicit with others as they will not change)
class GsComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with AhcWSComponents
    with HttpFiltersComponents
    with _root_.controllers.AssetsComponents {
  private val logger = LoggerFactory.getLogger(this.getClass)
  logger.info("Start application")

  // unsafe init should be done at the beginning
  lazy val conf: AppConf = AppConf.load(configuration).get
  lazy val appConf: ApplicationConf = conf.app

  lazy val db: GsRepoSql = new GsRepoSql(conf.database, conf.gospeak)
  lazy val userRepo: UserRepo = db.user
  lazy val userRequestRepo: UserRequestRepo = db.userRequest
  lazy val groupRepo: GroupRepo = db.group
  lazy val groupSettingsRepo: GroupSettingsRepo = db.groupSettings
  lazy val eventRepo: EventRepo = db.event
  lazy val cfpRepo: CfpRepo = db.cfp
  lazy val talkRepo: TalkRepo = db.talk
  lazy val proposalRepo: ProposalRepo = db.proposal
  lazy val partnerRepo: PartnerRepo = db.partner
  lazy val venueRepo: VenueRepo = db.venue
  lazy val sponsorPackRepo: SponsorPackRepo = db.sponsorPack
  lazy val sponsorRepo: SponsorRepo = db.sponsor
  lazy val contactRepo: ContactRepo = db.contact
  lazy val commentRepo: CommentRepo = db.comment
  lazy val externalEventRepo: ExternalEventRepo = db.externalEvent
  lazy val externalCfpRepo: ExternalCfpRepo = db.externalCfp
  lazy val externalProposalRepo: ExternalProposalRepo = db.externalProposal
  lazy val videoRepo: VideoRepo = db.video
  lazy val authRepo: AuthRepo = wire[AuthRepo]

  lazy val http: HttpClient = new HttpClientImpl()
  lazy val embedSrv: EmbedSrv = wire[EmbedSrv]
  lazy val scraperSrv: ScraperSrv = wire[ScraperSrv]
  lazy val avatarSrv: AvatarSrv = wire[AvatarSrv]
  lazy val uploadSrv: UploadSrv = UploadSrvFactory.from(conf.upload, http)
  lazy val emailSrv: EmailSrv = EmailSrvFactory.from(conf.email)
  lazy val twitterSrv: TwitterSrv = TwitterSrvFactory.from(conf.twitter)
  lazy val meetupSrv: Either[String, MeetupSrv] = MeetupSrvImpl.from(conf.meetup, conf.app.baseUrl, http, conf.app.env.isProd)
  lazy val placesSrv: PlacesSrv = PlacesSrvImpl.from(conf.googleMaps)
  lazy val videoSrv: VideoSrv = VideoSrvImpl.from(conf.app.name, conf.youtube).get
  lazy val slackSrv: SlackSrv = new SlackSrvImpl(new SlackClient(http))
  lazy val messageSrv: MessageSrv = wire[MessageSrv]
  lazy val messageBus: MessageBus[Message] = wire[BasicMessageBus[Message]]
  lazy val messageHandler: MessageHandler = wire[MessageHandler]
  lazy val schedulerSrv: SchedulerSrv = new SchedulerSrv(videoRepo, twitterSrv)(ExecutionContext.global)

  // start:Silhouette conf
  lazy val clock: Clock = Clock()
  lazy val idGenerator: SecureRandomIDGenerator = new SecureRandomIDGenerator()

  /* lazy val jwtAuth: AuthenticatorService[JWTAuthenticator] = {
    lazy val authenticatorDecoder: Base64AuthenticatorEncoder = new Base64AuthenticatorEncoder()
    val duration = configuration.underlying.getString("silhouette.jwt.authenticator.authenticatorExpiry")
    val expiration = Duration.apply(duration).asInstanceOf[FiniteDuration]
    val config = JWTAuthenticatorSettings(fieldName = configuration.underlying.getString("silhouette.jwt.authenticator.headerName"),
      issuerClaim = configuration.underlying.getString("silhouette.jwt.authenticator.issuerClaim"),
      authenticatorExpiry = expiration,
      sharedSecret = configuration.underlying.getString("silhouette.jwt.authenticator.sharedSecret"))
    new JWTAuthenticatorService(config, None, authenticatorDecoder, idGenerator, clock)
  } */
  val signer: Signer = new JcaSigner(conf.auth.cookie.signer)
  val crypter: Crypter = new JcaCrypter(conf.auth.cookie.crypter)
  lazy val cookieAuth: AuthenticatorService[CookieAuthenticator] = {
    val authenticatorEncoder = new CrypterAuthenticatorEncoder(crypter)
    val cookieHeaderEncoding: CookieHeaderEncoding = new DefaultCookieHeaderEncoding()
    val fingerprintGenerator: FingerprintGenerator = new DefaultFingerprintGenerator(false)
    new CookieAuthenticatorService(conf.auth.cookie.authenticator.toConf, None, signer, cookieHeaderEncoding, authenticatorEncoder, fingerprintGenerator, idGenerator, clock)
  }
  lazy val socialProviderRegistry: SocialProviderRegistry = {
    // val csrfStateSettings = authConf.cookie.authenticator.toCsrfStateSettings
    val csrfStateSettings = CsrfStateSettings(
      configuration.underlying.getString("silhouette.csrfStateItemHandler.cookieName"),
      configuration.underlying.getString("silhouette.csrfStateItemHandler.cookiePath"),
      None,
      configuration.underlying.getBoolean("silhouette.csrfStateItemHandler.secureCookie"),
      configuration.underlying.getBoolean("silhouette.csrfStateItemHandler.httpOnlyCookie"),
      SameSite.parse(configuration.underlying.getString("silhouette.csrfStateItemHandler.sameSite")),
      new FiniteDuration(configuration.underlying.getDuration("silhouette.csrfStateItemHandler.expirationTime").getSeconds, TimeUnit.SECONDS))
    val csrfStateItemHandler = new CsrfStateItemHandler(csrfStateSettings, idGenerator, signer)
    val socialStateHandler = new DefaultSocialStateHandler(Set(csrfStateItemHandler), signer)
    val cookieSecretProvider = new CookieSecretProvider(conf.auth.cookie.authenticator.toCookieSecretSettings, signer, crypter, clock)
    val httpLayer: HTTPLayer = new PlayHTTPLayer(wsClient)
    val providers: List[AuthConf.SocialConf] = List(
      conf.auth.google,
      conf.auth.twitter,
      conf.auth.facebook,
      // conf.auth.linkedin, // FIXME LinkedinProvider needs a little hack to get working with silhouette
      conf.auth.github)
    SocialProviderRegistry(providers.map(_.toProvider(conf.app.baseUrl, httpLayer, socialStateHandler, cookieSecretProvider)))
  }

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

  lazy val authSrv: AuthSrv = AuthSrv(conf.auth, silhouette, userRepo, userRequestRepo, groupRepo, authRepo, clock, socialProviderRegistry, avatarSrv)

  lazy val styleguideCtrl = wire[styleguide.StyleguideCtrl]
  lazy val homeCtrl = wire[published.HomeCtrl]
  lazy val utilsCtrl = wire[published.UtilsCtrl]
  lazy val cfpCtrl = wire[published.cfps.CfpCtrl]
  lazy val eventCtrl = wire[published.events.EventCtrl]
  lazy val groupCtrl = wire[published.groups.GroupCtrl]
  lazy val speakerCtrl = wire[published.speakers.SpeakerCtrl]
  lazy val videoCtrl = wire[published.videos.VideoCtrl]
  lazy val authCtrl = wire[AuthCtrl]
  lazy val userCtrl = wire[user.UserCtrl]
  lazy val userTalkCtrl = wire[user.talks.TalkCtrl]
  lazy val userTalkCfpCtrl = wire[user.talks.cfps.CfpCtrl]
  lazy val userTalkProposalCtrl = wire[user.talks.proposals.ProposalCtrl]
  lazy val userProposalCtrl = wire[user.proposals.ProposalCtrl]
  lazy val userProfileCtrl = wire[user.profile.ProfileCtrl]
  lazy val orgaGroupCtrl = wire[orga.GroupCtrl]
  lazy val orgaEventCtrl = wire[orga.events.EventCtrl]
  lazy val orgaCfpCtrl = wire[orga.cfps.CfpCtrl]
  lazy val orgaCfpProposalCtrl = wire[orga.cfps.proposals.ProposalCtrl]
  lazy val orgaProposalCtrl = wire[orga.proposals.ProposalCtrl]
  lazy val orgaSpeakerCtrl = wire[orga.speakers.SpeakerCtrl]
  lazy val orgaPartnerCtrl = wire[orga.partners.PartnerCtrl]
  lazy val orgaSponsorCtrl = wire[orga.sponsors.SponsorCtrl]
  lazy val orgaSettingsCtrl = wire[orga.settings.SettingsCtrl]
  lazy val adminCtrl = wire[admin.AdminCtrl]
  lazy val uiSuggestCtrl = wire[api.ui.SuggestCtrl]
  lazy val uiUtilsCtrl = wire[api.ui.UtilsCtrl]

  lazy val apiStatusCtrl = wire[api.StatusCtrl]
  lazy val apiSwaggerCtrl = wire[api.swagger.SwaggerCtrl]
  lazy val apiGroupCtrl = wire[api.published.GroupCtrl]
  lazy val apiEventCtrl = wire[api.published.EventCtrl]
  lazy val apiCfpCtrl = wire[api.published.CfpCtrl]
  lazy val apiSpeakerCtrl = wire[api.published.SpeakerCtrl]
  lazy val apiOrgaEventCtrl = wire[api.orga.ApiEventCtrl]
  lazy val apiOrgaCfpCtrl = wire[api.orga.ApiCfpCtrl]
  lazy val apiOrgaProposalCtrl = wire[api.orga.ApiProposalCtrl]

  override lazy val router: Router = {
    val prefix = "/"
    wire[Routes]
  }

  def onStart(): Unit = {
    val env = conf.app.env
    db.checkEnv(env).unsafeRunSync()
    env match {
      case ApplicationConf.Env.Prod | ApplicationConf.Env.Staging =>
        db.migrate().unsafeRunSync()
      case ApplicationConf.Env.Dev | ApplicationConf.Env.Local =>
        db.dropTables().unsafeRunSync()
        db.migrate().unsafeRunSync()
        db.insertMockData().unsafeRunSync()
    }

    messageBus.subscribe(messageHandler.logHandler)
    messageBus.subscribe(messageHandler.gospeakHandler)
    messageBus.subscribe(messageHandler.groupActionHandler)
    schedulerSrv.init(conf.scheduler)

    logger.info("Application initialized")
  }

  onStart()


  applicationLifecycle.addStopHook(() => Future {
    logger.info("Shutting down application")
    placesSrv.close()
  })
}
