package fr.gospeak.web.auth

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest.PasswordResetRequest
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthCtrl._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.emails.Emails
import fr.gospeak.web.auth.exceptions.{DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.utils.{HttpUtils, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

// TODO Avatar srv
// TODO Test this controller
// TODO Social auth
// TODO JWT Auth for API
class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               db: GospeakDb,
               authSrv: AuthSrv,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette) {
  private val userHome = fr.gospeak.web.user.routes.UserCtrl.index()

  private def loginRedirect(redirect: Option[String]): Result = Redirect(redirect.getOrElse(userHome.path()))

  private val logoutRedirect = Redirect(fr.gospeak.web.routes.HomeCtrl.index())

  import silhouette._

  def signup(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.signup(AuthForms.signup, redirect)(header)))
  }

  def doSignup(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.signup(formWithErrors, redirect)(header))),
      data => (for {
        user <- authSrv.createIdentity(data, now).unsafeToFuture()
        emailValidation <- db.createAccountValidationRequest(user.user.email, user.user.id, now).unsafeToFuture()
        _ <- emailSrv.send(Emails.signup(user, emailValidation)).unsafeToFuture()
        result <- authSrv.login(user, data.rememberMe, loginRedirect(redirect))
      } yield result).recover {
        case _: DuplicateIdentityException => BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError("User already exists"), redirect)(header))
        case e: DuplicateSlugException => BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"Username ${e.slug.value} is already taken"), redirect)(header))
        case NonFatal(e) => BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect)(header))
      }
    )
  }

  def login(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.login(AuthForms.login, redirect)(header)))
  }

  def doLogin(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.login(formWithErrors, redirect)(header))),
      data => (for {
        user <- authSrv.getIdentity(data)
        result <- authSrv.login(user, data.rememberMe, loginRedirect(redirect))
      } yield result).recover {
        case _: IdentityNotFoundException => Ok(html.login(AuthForms.login.fill(data).withGlobalError("Wrong login or password"), redirect)(header))
        case _: InvalidPasswordException => Ok(html.login(AuthForms.login.fill(data).withGlobalError("Wrong login or password"), redirect)(header))
        case NonFatal(e) => Ok(html.login(AuthForms.login.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect)(header))
      }
    )
  }

  def doLogout(): Action[AnyContent] = SecuredAction.async { implicit req =>
    authSrv.logout(logoutRedirect)
  }

  def accountValidation(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      existingValidationOpt <- db.getPendingAccountValidationRequest(req.identity.user.id, now)
      emailValidation <- existingValidationOpt.map(IO.pure).getOrElse(db.createAccountValidationRequest(req.identity.user.email, req.identity.user.id, now))
      _ <- emailSrv.send(Emails.accountValidation(req.identity, emailValidation))
    } yield loginRedirect(HttpUtils.getReferer(req)).flashing("success" -> "Email validation sent!")).unsafeToFuture()
  }

  def doValidateAccount(id: UserRequest.Id): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    (for {
      validation <- OptionT(db.getPendingAccountValidationRequest(id, now))
      _ <- OptionT.liftF(db.validateAccount(id, validation.user, now))
    } yield Redirect(routes.AuthCtrl.login()).flashing("success" -> s"Well done! You validated your email.")).value.map(_.getOrElse(notFound())).unsafeToFuture()
  }

  def forgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.forgotPassword(AuthForms.forgotPassword, redirect)(header)))
  }

  def doForgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    AuthForms.forgotPassword.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.forgotPassword(formWithErrors, redirect)(header))),
      data => (for {
        credentials <- OptionT(db.getCredentials(AuthSrv.login(data.email)))
        user <- OptionT(db.getUser(credentials))
        existingPasswordResetOpt <- OptionT.liftF(db.getPendingPasswordResetRequest(data.email, now))
        passwordReset <- existingPasswordResetOpt.map(OptionT.pure(_): OptionT[IO, PasswordResetRequest]).getOrElse(OptionT.liftF(db.createPasswordResetRequest(data.email, now)))
        _ <- OptionT.liftF(emailSrv.send(Emails.forgotPassword(user, passwordReset)))
      } yield Redirect(routes.AuthCtrl.login()).flashing("success" -> "Reset password email sent!")).value
        .map(_.getOrElse(Redirect(routes.AuthCtrl.forgotPassword(redirect)).flashing("error" -> s"Email not found"))).unsafeToFuture()
    )
  }

  def resetPassword(id: UserRequest.Id): Action[AnyContent] = UserAwareAction.async { implicit req =>
    req.identity
      .map(_ => Future.successful(loginRedirect(None).flashing(req.flash)))
      .getOrElse(resetPasswordForm(id, AuthForms.resetPassword))
  }

  def doResetPassword(id: UserRequest.Id): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    AuthForms.resetPassword.bindFromRequest.fold(
      formWithErrors => resetPasswordForm(id, formWithErrors),
      data => (for {
        passwordReset <- OptionT(db.getPendingPasswordResetRequest(id, now).unsafeToFuture())
        user <- OptionT.liftF(authSrv.updateIdentity(data, passwordReset, now).unsafeToFuture())
        result <- OptionT.liftF(authSrv.login(user, data.rememberMe, loginRedirect(None)))
      } yield result).value.map(_.getOrElse(notFound()))
    )
  }

  private def resetPasswordForm(id: UserRequest.Id, form: Form[AuthForms.ResetPasswordData])(implicit req: UserAwareRequest[CookieEnv, AnyContent]): Future[Result] = {
    val now = Instant.now()
    db.getPendingPasswordResetRequest(id, now).map { passwordResetOpt =>
      passwordResetOpt.map { passwordReset =>
        Ok(html.resetPassword(passwordReset, form)(header))
      }.getOrElse {
        Redirect(routes.AuthCtrl.login()).flashing("error" -> "Reset password request expired, create it again.")
      }
    }.unsafeToFuture()
  }
}

object AuthCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.AuthCtrl.login())
}
