package fr.gospeak.web.auth

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest.PasswordResetRequest
import fr.gospeak.core.services.storage.{AuthGroupRepo, AuthUserRepo, AuthUserRequestRepo}
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.emails.Emails
import fr.gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.pages
import fr.gospeak.web.utils.{HttpUtils, UICtrl}
import play.api.data.Form
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

// TODO Social auth
// TODO JWT Auth for API
class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               userRepo: AuthUserRepo,
               userRequestRepo: AuthUserRequestRepo,
               groupRepo: AuthGroupRepo,
               authSrv: AuthSrv,
               emailSrv: EmailSrv,
               envConf: ApplicationConf.Env) extends UICtrl(cc, silhouette) {
  private val loginRedirect = (redirect: Option[String]) => Redirect(redirect.getOrElse(pages.user.routes.UserCtrl.index().path()))
  private val logoutRedirect = Redirect(pages.published.routes.HomeCtrl.index())

  import silhouette._

  def signup(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.signup(AuthForms.signup, redirect)))
  }

  def doSignup(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.signup(formWithErrors, redirect))),
      data => (for {
        user <- authSrv.createIdentity(data, now).unsafeToFuture()
        emailValidation <- userRequestRepo.createAccountValidationRequest(user.user.email, user.user.id, now).unsafeToFuture()
        _ <- emailSrv.send(Emails.signup(user, emailValidation)).unsafeToFuture()
        result <- authSrv.login(user, data.rememberMe, loginRedirect(redirect))
      } yield result).recoverWith {
        case e: AccountValidationRequiredException => authSrv.logout(e.identity, Redirect(routes.AuthCtrl.login(redirect)).flashing("warning" -> "Account created, you need to validate it by clicking on the email validation link"))
        case _: DuplicateIdentityException => Future.successful(BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError("User already exists"), redirect)))
        case e: DuplicateSlugException => Future.successful(BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"Username ${e.slug.value} is already taken"), redirect)))
        case NonFatal(e) => Future.successful(BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect)))
      }
    )
  }

  def login(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.login(AuthForms.login, envConf, redirect)))
  }

  def doLogin(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.login(formWithErrors, envConf, redirect))),
      data => (for {
        user <- authSrv.getIdentity(data)
        result <- authSrv.login(user, data.rememberMe, loginRedirect(redirect))
      } yield result).recover {
        case _: AccountValidationRequiredException => Ok(html.login(AuthForms.login.fill(data).withGlobalError("You need to validate your account by clicking on the email validation link"), envConf, redirect))
        case _: IdentityNotFoundException => BadRequest(html.login(AuthForms.login.fill(data).withGlobalError("Wrong login or password"), envConf, redirect))
        case _: InvalidPasswordException => BadRequest(html.login(AuthForms.login.fill(data).withGlobalError("Wrong login or password"), envConf, redirect))
        case NonFatal(e) => BadRequest(html.login(AuthForms.login.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), envConf, redirect))
      }
    )
  }

  def doLogout(): Action[AnyContent] = SecuredAction.async { implicit req =>
    authSrv.logout(logoutRedirect)
  }

  def accountValidation(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      existingValidationOpt <- userRequestRepo.findPendingAccountValidationRequest(user, now)
      emailValidation <- existingValidationOpt.map(IO.pure).getOrElse(userRequestRepo.createAccountValidationRequest(req.identity.user.email, by, now))
      _ <- emailSrv.send(Emails.accountValidation(req.identity, emailValidation))
    } yield loginRedirect(HttpUtils.getReferer(req)).flashing("success" -> "Email validation sent!")).unsafeToFuture()
  }

  def doValidateAccount(id: UserRequest.Id): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    (for {
      validation <- OptionT(userRequestRepo.findPendingAccountValidationRequest(id, now).unsafeToFuture())
      _ <- OptionT.liftF(userRequestRepo.validateAccount(id, validation.email, now).unsafeToFuture())
      user <- OptionT(userRepo.find(validation.email).unsafeToFuture())
      groups <- OptionT.liftF(groupRepo.list(user.id).unsafeToFuture())
      redirect = Redirect(routes.AuthCtrl.login()).flashing("success" -> s"Well done! You validated your email.")
      result <- OptionT.liftF(authSrv.login(AuthSrv.authUser(user, groups), rememberMe = false, redirect))
    } yield result).value.map(_.getOrElse(notFound()))
  }

  def forgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.forgotPassword(AuthForms.forgotPassword, redirect)))
  }

  def doForgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    AuthForms.forgotPassword.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.forgotPassword(formWithErrors, redirect))),
      data => (for {
        credentials <- OptionT(userRepo.findCredentials(AuthSrv.login(data.email)))
        user <- OptionT(userRepo.find(credentials))
        existingPasswordResetOpt <- OptionT.liftF(userRequestRepo.findPendingPasswordResetRequest(data.email, now))
        passwordReset <- existingPasswordResetOpt.map(OptionT.pure(_): OptionT[IO, PasswordResetRequest]).getOrElse(OptionT.liftF(userRequestRepo.createPasswordResetRequest(data.email, now)))
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
        passwordReset <- OptionT(userRequestRepo.findPendingPasswordResetRequest(id, now).unsafeToFuture())
        user <- OptionT.liftF(authSrv.updateIdentity(data, passwordReset, now).unsafeToFuture())
        result <- OptionT.liftF(authSrv.login(user, data.rememberMe, loginRedirect(None)))
      } yield result).value.map(_.getOrElse(notFound()))
    )
  }

  private def resetPasswordForm(id: UserRequest.Id, form: Form[AuthForms.ResetPasswordData])(implicit req: UserAwareRequest[CookieEnv, AnyContent]): Future[Result] = {
    val now = Instant.now()
    userRequestRepo.findPendingPasswordResetRequest(id, now).map { passwordResetOpt =>
      passwordResetOpt.map { passwordReset =>
        Ok(html.resetPassword(passwordReset, form))
      }.getOrElse {
        Redirect(routes.AuthCtrl.login()).flashing("error" -> "Reset password request expired, create it again.")
      }
    }.unsafeToFuture()
  }
}
