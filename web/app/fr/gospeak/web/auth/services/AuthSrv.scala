package fr.gospeak.web.auth.services

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, CredentialsProvider, SocialProvider, SocialProviderRegistry}
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import fr.gospeak.core.domain.User.{Login, ProviderId, ProviderKey}
import fr.gospeak.core.domain.UserRequest.PasswordResetRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.{AuthGroupRepo, AuthUserRepo, AuthUserRequestRepo}
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.auth.AuthConf
import fr.gospeak.web.auth.AuthForms.{LoginData, ResetPasswordData, SignupData}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv, SocialProfile}
import fr.gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.utils.{SecuredReq, UserAwareReq}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class AuthSrv(userRepo: AuthUserRepo,
              userRequestRepo: AuthUserRequestRepo,
              groupRepo: AuthGroupRepo,
              authRepo: AuthRepo,
              silhouette: Silhouette[CookieEnv],
              clock: Clock,
              authConf: AuthConf,
              passwordHasherRegistry: PasswordHasherRegistry,
              credentialsProvider: CredentialsProvider,
              socialProviderRegistry: SocialProviderRegistry,
              gravatarSrv: GravatarSrv) {
  implicit private val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def createIdentity(data: SignupData)(implicit req: UserAwareReq[AnyContent]): IO[AuthUser] = {
    val loginInfo = AuthSrv.loginInfo(data.email)
    val login = toDomain(loginInfo)
    val password = toDomain(passwordHasherRegistry.current.hash(data.password.decode))
    val credentials = User.Credentials(login, password)
    for {
      loginOpt <- userRepo.find(login)
      _ <- loginOpt.swap.toIO(DuplicateIdentityException(loginInfo)) // fail if login already exist
      emailOpt <- userRepo.find(data.email)
      slugOpt <- userRepo.find(data.slug)
      _ <- slugOpt.forall(s => emailOpt.exists(_.id == s.id)).toIO(DuplicateSlugException(data.slug)) // fail if slug exists for a different user from email
      avatar = gravatarSrv.getAvatar(data.email)
      user <- emailOpt.map { user =>
        userRepo.edit(user.copy(slug = data.slug, firstName = data.firstName, lastName = data.lastName, email = data.email, avatar = avatar), req.now)
      }.getOrElse {
        userRepo.create(data.data(avatar), req.now)
      }
      _ <- userRepo.createLoginRef(login, user.id)
      _ <- userRepo.createCredentials(credentials)
      groups <- groupRepo.list(user.id)
      authUser = AuthUser(loginInfo, user, groups)
      _ = silhouette.env.eventBus.publish(SignUpEvent(authUser, req))
    } yield authUser
  }

  def getIdentity(data: LoginData): IO[AuthUser] = {
    for {
      loginInfo <- IO.fromFuture(IO(credentialsProvider.authenticate(Credentials(data.email.value, data.password.decode))))
      userOpt <- IO.fromFuture(IO(authRepo.retrieve(loginInfo)))
      user <- userOpt.toIO(new IdentityNotFoundException(s"User could not be found with info $loginInfo"))
    } yield user
  }

  def login(user: AuthUser, rememberMe: Boolean, redirect: Result)(implicit req: UserAwareReq[AnyContent]): IO[(CookieAuthenticator, AuthenticatorResult)] = {
    for {
      _ <- if (user.shouldValidateEmail()) IO.raiseError(AccountValidationRequiredException(user)) else IO.pure(())
      authenticator <- IO.fromFuture(IO(silhouette.env.authenticatorService.create(user.loginInfo))).map {
        case auth if rememberMe => auth.copy(
          expirationDateTime = clock.now.plus(authConf.cookie.rememberMe.authenticatorExpiry.toMillis),
          idleTimeout = Some(authConf.cookie.rememberMe.authenticatorIdleTimeout),
          cookieMaxAge = Some(authConf.cookie.rememberMe.cookieMaxAge))
        case auth => auth
      }
      cookie <- IO.fromFuture(IO(silhouette.env.authenticatorService.init(authenticator)))
      result <- IO.fromFuture(IO(silhouette.env.authenticatorService.embed(cookie, redirect)))
      _ = silhouette.env.eventBus.publish(LoginEvent(user, req))
    } yield (authenticator, result)
  }

  def logout(redirect: Result)(implicit req: SecuredReq[AnyContent]): IO[AuthenticatorResult] = {
    silhouette.env.eventBus.publish(LogoutEvent(req.underlying.identity, req))
    IO.fromFuture(IO(silhouette.env.authenticatorService.discard(req.underlying.authenticator, redirect)))
  }

  def logout(identity: AuthUser, redirect: Result)(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    silhouette.env.eventBus.publish(LogoutEvent(identity, req))
    req.underlying.authenticator.map(auth => IO.fromFuture(IO(silhouette.env.authenticatorService.discard(auth, redirect)))).getOrElse(IO.pure(redirect))
  }

  def updateIdentity(data: ResetPasswordData, passwordReset: PasswordResetRequest)(implicit req: UserAwareReq[AnyContent]): IO[AuthUser] = {
    val loginInfo = AuthSrv.loginInfo(passwordReset.email)
    val login = toDomain(loginInfo)
    val password = toDomain(passwordHasherRegistry.current.hash(data.password.decode))
    val credentials = User.Credentials(login, password)
    for {
      _ <- userRepo.find(login).flatMap(_.toIO(new IdentityNotFoundException(s"Unable to find user for ${login.providerId}")))
      _ <- userRequestRepo.resetPassword(passwordReset, credentials, req.now)
      updatedUser <- userRepo.find(login).flatMap(_.toIO(new IdentityNotFoundException(s"Unable to find user for ${login.providerId}")))
      groups <- groupRepo.list(updatedUser.id)
      authUser = AuthUser(loginInfo, updatedUser, groups)
    } yield authUser
  }

  def createOrEdit(socialProfile: CommonSocialProfile): IO[AuthUser] = {
    def authUser(optUser: Option[User], login: Login): IO[AuthUser] = {
      val now = Instant.now()
      optUser.fold(
        for {
          data <- SocialProfile.from(socialProfile).toUserData.toIO
          u <- userRepo.create(data, now)
          request <- userRequestRepo.createAccountValidationRequest(u.email, u.id, now)
          _ <- userRequestRepo.validateAccount(request.id, u.email, now)
          _ <- userRepo.createLoginRef(login, u.id)
        } yield AuthUser(socialProfile.loginInfo, u, Seq())
      )(u =>
        for {
          groups <- groupRepo.list(u.id)
          u <- userRepo.edit(u.copy(
            firstName = socialProfile.firstName.getOrElse(u.firstName),
            lastName = socialProfile.lastName.getOrElse(u.lastName)),
            now = now)
        } yield AuthUser(socialProfile.loginInfo, u, groups)
      )
    }

    val login = Login(ProviderId(socialProfile.loginInfo.providerID),
      ProviderKey(socialProfile.loginInfo.providerKey))

    for {
      optUser <- userRepo.find(login)
      authUser <- authUser(optUser, login)
    } yield authUser
  }


  def socialProviders(): Seq[SocialProvider] = socialProviderRegistry.providers

  def provider(providerID: String): Option[SocialProvider] = socialProviders().find(_.id == providerID)

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(User.ProviderId(loginInfo.providerID), User.ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(User.Hasher(authInfo.hasher), User.PasswordValue(authInfo.password), authInfo.salt.map(User.Salt))
}

object AuthSrv {
  def apply(authConf: AuthConf,
            silhouette: Silhouette[CookieEnv],
            userRepo: AuthUserRepo,
            userRequestRepo: AuthUserRequestRepo,
            groupRepo: AuthGroupRepo,
            authRepo: AuthRepo,
            clock: Clock,
            socialProviderRegistry: SocialProviderRegistry,
            gravatarSrv: GravatarSrv): AuthSrv = {
    val authInfoRepository = new DelegableAuthInfoRepository(authRepo)
    val bCryptPasswordHasher: PasswordHasher = new BCryptPasswordHasher
    val passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(bCryptPasswordHasher)
    val credentialsProvider = new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
    new AuthSrv(userRepo, userRequestRepo, groupRepo, authRepo, silhouette, clock, authConf, passwordHasherRegistry, credentialsProvider, socialProviderRegistry, gravatarSrv)
  }

  def login(email: EmailAddress): User.Login = User.Login(ProviderId(CredentialsProvider.ID), ProviderKey(email.value))

  def loginInfo(email: EmailAddress): LoginInfo = new LoginInfo(CredentialsProvider.ID, email.value)

  def authUser(user: User, groups: Seq[Group]): AuthUser = AuthUser(loginInfo(user.email), user, groups)
}
