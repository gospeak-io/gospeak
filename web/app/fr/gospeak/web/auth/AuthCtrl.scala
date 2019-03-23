package fr.gospeak.web.auth

import java.time.Instant

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.util.{Clock, Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthCtrl._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.exceptions.{DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.{AuthRepo, AuthSrv}
import fr.gospeak.web.domain.{AuthCookieConf, HeaderInfo}
import fr.gospeak.web.utils.UICtrl
import play.api.i18n.Messages
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               authCookieConf: AuthCookieConf,
               credentialsProvider: CredentialsProvider,
               passwordHasherRegistry: PasswordHasherRegistry,
               authRepo: AuthRepo,
               authSrv: AuthSrv,
               clock: Clock,
               db: GospeakDb,
               emailSrv: EmailSrv) extends UICtrl(cc) {
  private val loginRedirect = Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
  private val logoutRedirect = Redirect(fr.gospeak.web.routes.HomeCtrl.index())

  def signup(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    authRepo.userAware() match {
      case Some(_) => loginRedirect
      case None => Ok(html.signup(AuthForms.signup)(header))
    }
  }

  def doSignup(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    println("doSignup")
    val now = Instant.now()
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.signup(formWithErrors)(header))),
      data => {
        // TODO redirect to signup page with a success message and send a validation email to accept email validation
        val loginInfo = new LoginInfo(CredentialsProvider.ID, data.email.value)
        (for {
          user <- authSrv.createIdentity(loginInfo, data, now).unsafeToFuture()
          authenticator <- silhouette.env.authenticatorService.create(loginInfo)
          _ = silhouette.env.eventBus.publish(SignUpEvent(user, req))
          _ = silhouette.env.eventBus.publish(LoginEvent(user, req))
          cookie <- silhouette.env.authenticatorService.init(authenticator)
          result <- silhouette.env.authenticatorService.embed(cookie, loginRedirect)
          _ <- authRepo.login(user.user).unsafeToFuture() // TODO remove
        } yield result).recover {
          case _: DuplicateIdentityException => Ok(html.signup(AuthForms.signup.fill(data))(header)(req, implicitly[Messages], Flash(Map("error" -> s"User already exists"))))
          case e: DuplicateSlugException => Ok(html.signup(AuthForms.signup.fill(data))(header)(req, implicitly[Messages], Flash(Map("error" -> s"Username ${e.slug.value} is already taken"))))
          case NonFatal(e) => Ok(html.signup(AuthForms.signup.fill(data))(header)(req, implicitly[Messages], Flash(Map("error" -> s"${e.getClass.getSimpleName}: ${e.getMessage}"))))
        }
      }
    )
  }

  def login(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    authRepo.userAware() match {
      case Some(_) => loginRedirect
      case None => Ok(html.login(AuthForms.login)(header))
    }
  }

  def doLogin(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    println("doLogin")
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.login(formWithErrors)(header))),
      data => (for {
        loginInfo <- credentialsProvider.authenticate(Credentials(data.email.value, data.password.decode))
        userOpt <- authRepo.retrieve(loginInfo)
        user <- userOpt.toFuture(new IdentityNotFoundException(s"User could not be found with info $loginInfo"))
        authenticator <- silhouette.env.authenticatorService.create(loginInfo).map {
          case auth if /* data.rememberMe */ true => auth.copy(
            expirationDateTime = clock.now.plus(authCookieConf.rememberMe.authenticatorExpiry.toMillis),
            idleTimeout = Some(authCookieConf.rememberMe.authenticatorIdleTimeout),
            cookieMaxAge = Some(authCookieConf.rememberMe.cookieMaxAge))
          case auth => auth
        }
        _ = silhouette.env.eventBus.publish(LoginEvent(user, req))
        cookie <- silhouette.env.authenticatorService.init(authenticator)
        result <- silhouette.env.authenticatorService.embed(cookie, loginRedirect)
        _ <- authRepo.login(user.user).unsafeToFuture() // TODO remove
      } yield result).recover {
        case _: IdentityNotFoundException => Ok(html.login(AuthForms.login.fill(data))(header)(req, implicitly[Messages], Flash(Map("error" -> "Wrong login or password"))))
        case _: InvalidPasswordException => Ok(html.login(AuthForms.login.fill(data))(header)(req, implicitly[Messages], Flash(Map("error" -> "Wrong login or password"))))
        case NonFatal(e) => Ok(html.login(AuthForms.login.fill(data))(header)(req, implicitly[Messages], Flash(Map("error" -> s"${e.getClass.getSimpleName}: ${e.getMessage}"))))
      }
    )
  }

  def passwordReset(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    authRepo.userAware() match {
      case Some(_) => loginRedirect
      case None => Ok(html.passwordReset(AuthForms.passwordReset)(header))
    }
  }

  def doPasswordReset(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Redirect(routes.AuthCtrl.login())
  }

  def doLogout(): Action[AnyContent] = Action.async { implicit req =>
    authRepo.logout().unsafeToFuture()
      .map { _ => logoutRedirect }
  }

  /* def doLogout(): Action[AnyContent] = silhouette.SecuredAction.async { implicit req =>
    authSrv.logout().unsafeToFuture().flatMap { _ =>
      silhouette.env.eventBus.publish(LogoutEvent(req.identity, req))
      silhouette.env.authenticatorService.discard(req.authenticator, logoutRedirect)
    }
  } */
}

object AuthCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.AuthCtrl.login())
}
