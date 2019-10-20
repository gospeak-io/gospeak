package fr.gospeak.web.auth

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest.PasswordResetRequest
import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.core.services.storage.{AuthGroupRepo, AuthUserRepo, AuthUserRequestRepo}
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.published.routes.{HomeCtrl => HomeRoutes}
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{HttpUtils, UICtrl, UserAwareReq}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

// TODO Social auth
// TODO JWT Auth for API
class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               env: ApplicationConf.Env,
               userRepo: AuthUserRepo,
               userRequestRepo: AuthUserRequestRepo,
               groupRepo: AuthGroupRepo,
               authSrv: AuthSrv,
               emailSrv: EmailSrv,
               envConf: ApplicationConf.Env) extends UICtrl(cc, silhouette, env) {
  private val loginRedirect = (redirect: Option[String]) => Redirect(redirect.getOrElse(UserRoutes.index().path()))
  private val logoutRedirect = Redirect(HomeRoutes.index())

  def signup(redirect: Option[String]): Action[AnyContent] = UserAwareActionIO { implicit req =>
    IO.pure(req.user
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.signup(AuthForms.signup, redirect))))
  }

  def doSignup(redirect: Option[String]): Action[AnyContent] = UserAwareActionIO { implicit req =>
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => IO.pure(BadRequest(html.signup(formWithErrors, redirect))),
      data => (for {
        user <- authSrv.createIdentity(data)
        emailValidation <- userRequestRepo.createAccountValidationRequest(user.user.email, user.user.id, req.now)
        (authenticator, result) <- authSrv.login(user, data.rememberMe, loginRedirect(redirect))
        secured = req.secured(user, authenticator)
        _ <- emailSrv.send(Emails.signup(emailValidation)(secured))
      } yield result: Result).recoverWith {
        case e: AccountValidationRequiredException => authSrv.logout(e.identity, Redirect(routes.AuthCtrl.login(redirect)).flashing("warning" -> "Account created, you need to validate it by clicking on the email validation link"))
        case _: DuplicateIdentityException => IO.pure(BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError("User already exists"), redirect)))
        case e: DuplicateSlugException => IO.pure(BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"Username ${e.slug.value} is already taken"), redirect)))
        case NonFatal(e) => IO.pure(BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect)))
      }
    )
  }

  def login(redirect: Option[String]): Action[AnyContent] = UserAwareActionIO { implicit req =>
    IO.pure(req.user
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.login(AuthForms.login, envConf, redirect, authSrv.socialProviders))))
  }

  def doLogin(redirect: Option[String]): Action[AnyContent] = UserAwareActionIO { implicit req =>
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => IO.pure(BadRequest(html.login(formWithErrors, envConf, redirect))),
      data => (for {
        user <- authSrv.getIdentity(data)
        (_, result) <- authSrv.login(user, data.rememberMe, loginRedirect(redirect))
      } yield result: Result).recover {
        case _: AccountValidationRequiredException => Ok(html.login(AuthForms.login.fill(data).withGlobalError("You need to validate your account by clicking on the email validation link"), envConf, redirect))
        case _: IdentityNotFoundException => BadRequest(html.login(AuthForms.login.fill(data).withGlobalError("Wrong login or password"), envConf, redirect))
        case _: InvalidPasswordException => BadRequest(html.login(AuthForms.login.fill(data).withGlobalError("Wrong login or password"), envConf, redirect))
        case NonFatal(e) => BadRequest(html.login(AuthForms.login.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), envConf, redirect))
      }
    )
  }

  def doLogout(): Action[AnyContent] = SecuredActionIO { implicit req =>
    authSrv.logout(logoutRedirect)
  }

  def accountValidation(): Action[AnyContent] = SecuredActionIO { implicit req =>
    for {
      existingValidationOpt <- userRequestRepo.findPendingAccountValidationRequest(req.user.id, req.now)
      emailValidation <- existingValidationOpt.map(IO.pure).getOrElse(userRequestRepo.createAccountValidationRequest(req.user.email, req.user.id, req.now))
      _ <- emailSrv.send(Emails.accountValidation(emailValidation))
    } yield loginRedirect(HttpUtils.getReferer(req)).flashing("success" -> "Email validation sent!")
  }

  def doValidateAccount(id: UserRequest.Id): Action[AnyContent] = UserAwareActionIO { implicit req =>
    (for {
      validation <- OptionT(userRequestRepo.findAccountValidationRequest(id))
      msg <- if (validation.isPending(req.now)) {
        OptionT.liftF(userRequestRepo.validateAccount(id, validation.email, req.now).map(_ => "success" -> s"Well done! You validated your email."))
      } else if(validation.deadline.isBefore(req.now)) {
        OptionT.liftF(IO.pure("error" -> "Expired deadline for email validation. Please ask to resend the validation email."))
      } else if(validation.accepted.nonEmpty) {
        OptionT.liftF(IO.pure("error" -> s"This validation was already used. Please contact <b>${Constants.Contact.admin.address.value}</b> if this is not expected."))
      } else {
        OptionT.liftF(IO.pure("error" -> "Can't validate your email, please ask to resend the validation email."))
      }
      res = Redirect(UserRoutes.index()).flashing(msg)
    } yield res).value.map(_.getOrElse(notFound()))
  }

  def forgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareActionIO { implicit req =>
    IO.pure(req.user
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.forgotPassword(AuthForms.forgotPassword, redirect))))
  }

  def doForgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareActionIO { implicit req =>
    AuthForms.forgotPassword.bindFromRequest.fold(
      formWithErrors => IO.pure(BadRequest(html.forgotPassword(formWithErrors, redirect))),
      data => (for {
        credentials <- OptionT(userRepo.findCredentials(AuthSrv.login(data.email)))
        user <- OptionT(userRepo.find(credentials))
        existingPasswordResetOpt <- OptionT.liftF(userRequestRepo.findPendingPasswordResetRequest(data.email, req.now))
        passwordReset <- existingPasswordResetOpt.map(OptionT.pure(_): OptionT[IO, PasswordResetRequest]).getOrElse(OptionT.liftF(userRequestRepo.createPasswordResetRequest(data.email, req.now)))
        _ <- OptionT.liftF(emailSrv.send(Emails.forgotPassword(user, passwordReset)))
      } yield Redirect(routes.AuthCtrl.login()).flashing("success" -> "Reset password email sent!")).value
        .map(_.getOrElse(Redirect(routes.AuthCtrl.forgotPassword(redirect)).flashing("error" -> s"Email not found")))
    )
  }

  def resetPassword(id: UserRequest.Id): Action[AnyContent] = UserAwareActionIO { implicit req =>
    req.user
      .map(_ => IO.pure(loginRedirect(None).flashing(req.flash)))
      .getOrElse(resetPasswordForm(id, AuthForms.resetPassword))
  }

  def doResetPassword(id: UserRequest.Id): Action[AnyContent] = UserAwareActionIO { implicit req =>
    AuthForms.resetPassword.bindFromRequest.fold(
      formWithErrors => resetPasswordForm(id, formWithErrors),
      data => (for {
        passwordReset <- OptionT(userRequestRepo.findPendingPasswordResetRequest(id, req.now))
        user <- OptionT.liftF(authSrv.updateIdentity(data, passwordReset))
        (_, result) <- OptionT.liftF(authSrv.login(user, data.rememberMe, loginRedirect(None)))
      } yield result).value.map(_.getOrElse(notFound()))
    )
  }

  private def resetPasswordForm(id: UserRequest.Id, form: Form[AuthForms.ResetPasswordData])(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    userRequestRepo.findPendingPasswordResetRequest(id, req.now).map { passwordResetOpt =>
      passwordResetOpt.map { passwordReset =>
        Ok(html.resetPassword(passwordReset, form))
      }.getOrElse {
        Redirect(routes.AuthCtrl.login()).flashing("error" -> "Reset password request expired, create it again.")
      }
    }
  }
}
