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
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Email
import fr.gospeak.web.auth.AuthForms.{LoginData, SignupData}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.exceptions.{DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.domain.AuthCookieConf
import play.api.mvc.{AnyContent, Request, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthSrv(authRepo: AuthRepo,
              db: GospeakDb,
              silhouette: Silhouette[CookieEnv],
              clock: Clock,
              authCookieConf: AuthCookieConf,
              passwordHasherRegistry: PasswordHasherRegistry,
              credentialsProvider: CredentialsProvider) {
  def createIdentity(data: SignupData, now: Instant)(implicit req: Request[AnyContent]): IO[AuthUser] = {
    val loginInfo = AuthSrv.loginInfo(data.email)
    val login = toDomain(loginInfo)
    val password = toDomain(passwordHasherRegistry.current.hash(data.password.decode))
    val credentials = User.Credentials(login, password)
    for {
      loginOpt <- db.getUser(login)
      _ <- loginOpt.swap.toIO(DuplicateIdentityException(loginInfo)) // fail if login already exist
      emailOpt <- db.getUser(data.email)
      slugOpt <- db.getUser(data.slug)
      _ <- slugOpt.forall(s => emailOpt.exists(_.id == s.id)).toIO(DuplicateSlugException(data.slug)) // fail if slug exists for a different user from email
      user <- emailOpt.map { user =>
        db.updateUser(user.copy(slug = data.slug, firstName = data.firstName, lastName = data.lastName, email = data.email), now)
      }.getOrElse {
        db.createUser(data.slug, data.firstName, data.lastName, data.email, now)
      }
      _ <- db.createLoginRef(login, user.id)
      _ <- db.createCredentials(credentials)
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

  def login(user: AuthUser, redirect: Result)(implicit req: Request[AnyContent]): Future[AuthenticatorResult] = {
    for {
      authenticator <- silhouette.env.authenticatorService.create(user.loginInfo).map {
        case auth if /* data.rememberMe */ true => auth.copy(
          expirationDateTime = clock.now.plus(authCookieConf.rememberMe.authenticatorExpiry.toMillis),
          idleTimeout = Some(authCookieConf.rememberMe.authenticatorIdleTimeout),
          cookieMaxAge = Some(authCookieConf.rememberMe.cookieMaxAge))
        case auth => auth
      }
      cookie <- silhouette.env.authenticatorService.init(authenticator)
      result <- silhouette.env.authenticatorService.embed(cookie, redirect)
      _ = silhouette.env.eventBus.publish(LoginEvent(user, req))
      _ <- authRepo.login(user.user).unsafeToFuture() // TODO remove
    } yield result
  }

  def logout(redirect: Result)(implicit req: SecuredRequest[CookieEnv, AnyContent]): Future[AuthenticatorResult] = {
    authRepo.logout().unsafeRunSync() // TODO remove
    silhouette.env.eventBus.publish(LogoutEvent(req.identity, req))
    silhouette.env.authenticatorService.discard(req.authenticator, redirect)
  }

  private def toDomain(loginInfo: LoginInfo): User.Login = User.Login(User.ProviderId(loginInfo.providerID), User.ProviderKey(loginInfo.providerKey))

  private def toDomain(authInfo: PasswordInfo): User.Password = User.Password(User.Hasher(authInfo.hasher), User.PasswordValue(authInfo.password), authInfo.salt.map(User.Salt))
}

object AuthSrv {
  def apply(authCookieConf: AuthCookieConf, silhouette: Silhouette[CookieEnv], db: GospeakDb, authRepo: AuthRepo, clock: Clock): AuthSrv = {
    val authInfoRepository = new DelegableAuthInfoRepository(authRepo)
    val bCryptPasswordHasher: PasswordHasher = new BCryptPasswordHasher
    val passwordHasherRegistry: PasswordHasherRegistry = PasswordHasherRegistry(bCryptPasswordHasher)
    val credentialsProvider = new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
    new AuthSrv(authRepo, db, silhouette, clock, authCookieConf, passwordHasherRegistry, credentialsProvider)
  }

  def loginInfo(email: Email): LoginInfo = new LoginInfo(CredentialsProvider.ID, email.value)
}
