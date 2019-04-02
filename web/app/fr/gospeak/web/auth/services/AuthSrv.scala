package fr.gospeak.web.auth.services

import java.time.Instant

import cats.effect.IO
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User.{ProviderId, ProviderKey}
import fr.gospeak.core.domain.UserRequest.PasswordResetRequest
import fr.gospeak.core.services.{UserRepo, UserRequestRepo}
import fr.gospeak.infra.services.GravatarSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.EmailAddress
import fr.gospeak.web.auth.AuthConf
import fr.gospeak.web.auth.AuthForms.{LoginData, ResetPasswordData, SignupData}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.exceptions.{DuplicateIdentityException, DuplicateSlugException}
import play.api.mvc.{AnyContent, Request, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthSrv(authRepo: AuthRepo,
              userRepo: UserRepo,
              userRequestRepo: UserRequestRepo,
              silhouette: Silhouette[CookieEnv],
              clock: Clock,
              authConf: AuthConf,
              passwordHasherRegistry: PasswordHasherRegistry,
              credentialsProvider: CredentialsProvider,
              gravatarSrv: GravatarSrv) {
  def createIdentity(data: SignupData, now: Instant)(implicit req: Request[AnyContent]): IO[AuthUser] = {
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
        userRepo.update(user.copy(slug = data.slug, firstName = data.firstName, lastName = data.lastName, email = data.email, avatar = avatar), now)
      }.getOrElse {
        userRepo.create(data.slug, data.firstName, data.lastName, data.email, avatar, now)
      }
      _ <- userRepo.createLoginRef(login, user.id)
      _ <- userRepo.createCredentials(credentials)
      authUser = AuthUser(loginInfo, user)
      _ = silhouette.env.eventBus.publish(SignUpEvent(authUser, req))
    } yield authUser
  }

  def getIdentity(data: LoginData): Future[AuthUser] = {
    for {
      loginInfo <- credentialsProvider.authenticate(Credentials(data.email.value, data.password.decode))
      userOpt <- authRepo.retrieve(loginInfo)
      user <- userOpt.toFuture(new IdentityNotFoundException(s"User could not be found with info $loginInfo"))
    } yield user
  }

  def login(user: AuthUser, rememberMe: Boolean, redirect: Result)(implicit req: Request[AnyContent]): Future[AuthenticatorResult] = {
    for {
      authenticator <- silhouette.env.authenticatorService.create(user.loginInfo).map {
        case auth if rememberMe => auth.copy(
          expirationDateTime = clock.now.plus(authConf.cookie.rememberMe.authenticatorExpiry.toMillis),
          idleTimeout = Some(authConf.cookie.rememberMe.authenticatorIdleTimeout),
          cookieMaxAge = Some(authConf.cookie.rememberMe.cookieMaxAge))
        case auth => auth
      }
      cookie <- silhouette.env.authenticatorService.init(authenticator)
      result <- silhouette.env.authenticatorService.embed(cookie, redirect)
      _ = silhouette.env.eventBus.publish(LoginEvent(user, req))
    } yield result
  }

  def logout(redirect: Result)(implicit req: SecuredRequest[CookieEnv, AnyContent]): Future[AuthenticatorResult] = {
    silhouette.env.eventBus.publish(LogoutEvent(req.identity, req))
    silhouette.env.authenticatorService.discard(req.authenticator, redirect)
  }

  def updateIdentity(data: ResetPasswordData, passwordReset: PasswordResetRequest, now: Instant)(implicit req: Request[AnyContent]): IO[AuthUser] = {
    val loginInfo = AuthSrv.loginInfo(passwordReset.email)
    val login = toDomain(loginInfo)
    val password = toDomain(passwordHasherRegistry.current.hash(data.password.decode))
    val credentials = User.Credentials(login, password)
    for {
      user <- userRepo.find(login).flatMap(_.toIO(new IdentityNotFoundException(s"Unable to find user for ${login.providerId}")))
      _ <- userRequestRepo.resetPassword(passwordReset, credentials, now)
      authUser = AuthUser(loginInfo, user)
    } yield authUser
  }

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(User.ProviderId(loginInfo.providerID), User.ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(User.Hasher(authInfo.hasher), User.PasswordValue(authInfo.password), authInfo.salt.map(User.Salt))
}

object AuthSrv {
  def apply(authConf: AuthConf, silhouette: Silhouette[CookieEnv], userRepo: UserRepo, userRequestRepo: UserRequestRepo, authRepo: AuthRepo, clock: Clock, gravatarSrv: GravatarSrv): AuthSrv = {
    val authInfoRepository = new DelegableAuthInfoRepository(authRepo)
    val bCryptPasswordHasher: PasswordHasher = new BCryptPasswordHasher
    val passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(bCryptPasswordHasher)
    val credentialsProvider = new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
    new AuthSrv(authRepo, userRepo, userRequestRepo, silhouette, clock, authConf, passwordHasherRegistry, credentialsProvider, gravatarSrv)
  }

  def login(email: EmailAddress): User.Login = User.Login(ProviderId(CredentialsProvider.ID), ProviderKey(email.value))

  def loginInfo(email: EmailAddress): LoginInfo = new LoginInfo(CredentialsProvider.ID, email.value)
}
