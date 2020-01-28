package fr.gospeak.web.auth.services

import cats.effect.{ContextShift, IO}
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, CredentialsProvider, SocialProviderRegistry}
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import fr.gospeak.core.domain.User.{Login, ProviderId, ProviderKey}
import fr.gospeak.core.domain.UserRequest.PasswordResetRequest
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.storage.{AuthGroupRepo, AuthUserRepo, AuthUserRequestRepo}
import fr.gospeak.infra.services.AvatarSrv
import fr.gospeak.web.auth.AuthConf
import fr.gospeak.web.auth.AuthForms.{LoginData, ResetPasswordData, SignupData}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv, SocialProfile}
import fr.gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.utils.{UserAwareReq, UserReq}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, EmailAddress}
import org.apache.http.auth.AuthenticationException
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

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
              avatarSrv: AvatarSrv) {
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
      avatar = avatarSrv.getDefault(data.email, data.slug)
      user <- emailOpt.map { user =>
        userRepo.edit(user.id)(user.data.copy(slug = data.slug, firstName = data.firstName, lastName = data.lastName, email = data.email, avatar = avatar), req.now)
      }.getOrElse {
        userRepo.create(data.data(avatar), req.now, None)
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

  def login(user: AuthUser, rememberMe: Boolean, redirect: UserReq[AnyContent] => IO[Result])
           (implicit req: UserAwareReq[AnyContent]): IO[(CookieAuthenticator, AuthenticatorResult)] = {
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
      next <- redirect(req.secured(user, authenticator))
      result <- IO.fromFuture(IO(silhouette.env.authenticatorService.embed(cookie, next)))
      _ = silhouette.env.eventBus.publish(LoginEvent(user, req))
    } yield (authenticator, result)
  }

  def logout(redirect: IO[Result])(implicit req: UserReq[AnyContent]): IO[AuthenticatorResult] = {
    silhouette.env.eventBus.publish(LogoutEvent(req.underlying.identity, req))
    for {
      next <- redirect
      res <- IO.fromFuture(IO(silhouette.env.authenticatorService.discard(req.underlying.authenticator, next)))
    } yield res
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

  def createOrEdit(profile: CommonSocialProfile)(implicit req: UserAwareReq[AnyContent]): IO[AuthUser] = {
    def create(login: Login): IO[AuthUser] = {
      for {
        data <- SocialProfile.toUserData(profile, avatarSrv.getDefault, req.now).toIO
        exists <- userRepo.find(data.email)
        user <- exists.fold(userRepo.create(data, req.now, Some(req.now)))(u => userRepo.edit(u.id)(u.data, req.now))
        _ <- userRepo.createLoginRef(login, user.id)
        groups <- groupRepo.list(user.id)
      } yield AuthUser(profile.loginInfo, user, groups)
    }

    def edit(login: Login)(user: User): IO[AuthUser] = {
      for {
        email <- profile.email.map(EmailAddress.from).getOrElse(Right(user.email)).toIO // should update email address when login with social provider???
        avatarOpt <- SocialProfile.getAvatar(profile).toIO
        u <- userRepo.edit(user.id)(user.data.copy(
          firstName = profile.firstName.getOrElse(user.firstName),
          lastName = profile.lastName.getOrElse(user.lastName),
          email = email,
          avatar = avatarOpt.getOrElse(user.avatar)), now = req.now)
        groups <- groupRepo.list(user.id)
      } yield AuthUser(profile.loginInfo, u, groups)
    }

    val login = Login(ProviderId(profile.loginInfo.providerID), ProviderKey(profile.loginInfo.providerKey))
    for {
      optUser <- userRepo.find(login)
      authUser <- optUser.fold(create(login))(edit(login))
    } yield authUser
  }

  def providerIds: Seq[String] = socialProviderRegistry.providers.map(_.id)

  def authenticate(providerID: String)(implicit req: UserAwareReq[AnyContent]): IO[Either[Result, CommonSocialProfile]] = {
    for {
      provider <- socialProviderRegistry.providers.find(_.id == providerID)
        .toIO(new AuthenticationException(s"Cannot authenticate with social provider: $providerID (provider not found)"))
      res <- IO.fromFuture(IO(provider.authenticate()))
      profile <- res.map(authInfo => IO.fromFuture(IO(provider.retrieveProfile(authInfo)))).sequence
      commonProfile <- profile.map(p => Try(p.asInstanceOf[CommonSocialProfile])
        .mapFailure(_ => CustomException("Oops. Something went wrong. Cannot retrieve the social profile.")).toIO).sequence
    } yield commonProfile
  }

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
            avatarSrv: AvatarSrv): AuthSrv = {
    val authInfoRepository = new DelegableAuthInfoRepository(authRepo)
    val bCryptPasswordHasher: PasswordHasher = new BCryptPasswordHasher
    val passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(bCryptPasswordHasher)
    val credentialsProvider = new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
    new AuthSrv(userRepo, userRequestRepo, groupRepo, authRepo, silhouette, clock, authConf, passwordHasherRegistry, credentialsProvider, socialProviderRegistry, avatarSrv)
  }

  def login(email: EmailAddress): User.Login = User.Login(ProviderId(CredentialsProvider.ID), ProviderKey(email.value))

  def loginInfo(email: EmailAddress): LoginInfo = new LoginInfo(CredentialsProvider.ID, email.value)

  def authUser(user: User, groups: Seq[Group]): AuthUser = AuthUser(loginInfo(user.email), user, groups)
}
