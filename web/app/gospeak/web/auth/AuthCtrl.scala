package gospeak.web.auth

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import gospeak.core.domain.UserRequest.PasswordResetRequest
import gospeak.core.domain.utils.Constants
import gospeak.core.domain.{User, UserRequest}
import gospeak.core.services.email.EmailSrv
import gospeak.core.services.storage.{AuthUserRepo, AuthUserRequestRepo}
import gospeak.libs.scala.domain.EmailAddress
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.auth.exceptions.{AccountValidationRequiredException, DuplicateIdentityException, DuplicateSlugException}
import gospeak.web.auth.services.AuthSrv
import gospeak.web.emails.Emails
import gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import gospeak.web.utils.{GsForms, UICtrl, UserAwareReq}
import play.api.data.Form
import play.api.mvc._

import scala.util.control.NonFatal

class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf,
               userRepo: AuthUserRepo,
               userRequestRepo: AuthUserRequestRepo,
               authSrv: AuthSrv,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.Auth {
  def signup(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    loggedRedirect(IO.pure(Ok(html.signup(GsForms.signup, redirect))), redirect)
  }

  def doSignup(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    GsForms.signup.bindFromRequest.fold(
      formWithErrors => IO.pure(BadRequest(html.signup(formWithErrors, redirect))),
      data => (for {
        user <- authSrv.createIdentity(data)
        emailValidation <- userRequestRepo.createAccountValidationRequest(user.user.email, user.user.id, req.now)
        _ <- emailSrv.send(Emails.signup(emailValidation, user.user))
        (_, result) <- authSrv.login(user, data.rememberMe, loggedRedirect(redirect)(_))
      } yield result: Result).recoverWith {
        case e: AccountValidationRequiredException => authSrv.logout(e.identity, Redirect(routes.AuthCtrl.login(redirect)).flashing("warning" -> "Account created, you need to validate it by clicking on the email validation link"))
        case _: DuplicateIdentityException => IO.pure(BadRequest(html.signup(GsForms.signup.fill(data).withGlobalError("User already exists"), redirect)))
        case e: DuplicateSlugException => IO.pure(BadRequest(html.signup(GsForms.signup.fill(data).withGlobalError(s"Username ${e.slug.value} is already taken"), redirect)))
        case NonFatal(e) => IO.pure(BadRequest(html.signup(GsForms.signup.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect)))
      }
    )
  }

  def login(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    loggedRedirect(IO.pure(Ok(html.login(GsForms.login, redirect, authSrv.providerIds))), redirect)
  }

  def doLogin(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    GsForms.login.bindFromRequest.fold(
      formWithErrors => IO.pure(BadRequest(html.login(formWithErrors, redirect, authSrv.providerIds))),
      data => (for {
        user <- authSrv.getIdentity(data)
        (_, result) <- authSrv.login(user, data.rememberMe, loggedRedirect(redirect)(_))
      } yield result: Result).recover {
        case _: AccountValidationRequiredException => Ok(html.login(GsForms.login.fill(data).withGlobalError(
          s"""You need to validate your account by clicking on the email validation link
             |<a href="${routes.AuthCtrl.resendEmailValidationExt(data.email)}" class="btn btn-danger btn-xs">Resend validation email</a>
             |""".stripMargin
        ), redirect, authSrv.providerIds))
        case _: IdentityNotFoundException => BadRequest(html.login(GsForms.login.fill(data).withGlobalError("Wrong login or password"), redirect, authSrv.providerIds))
        case _: InvalidPasswordException => BadRequest(html.login(GsForms.login.fill(data).withGlobalError("Wrong login or password"), redirect, authSrv.providerIds))
        case NonFatal(e) => BadRequest(html.login(GsForms.login.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect, authSrv.providerIds))
      }
    )
  }

  def authenticate(providerID: String): Action[AnyContent] = UserAwareAction { implicit req =>
    for {
      profileE <- authSrv.authenticate(providerID)
      result <- profileE.fold(IO.pure, profile => for {
        authUser <- authSrv.createOrEdit(profile)
        (_, authenticatorResult) <- authSrv.login(authUser, rememberMe = true, loggedRedirect(None)(_).map(_.flashing("success" -> s"Hi ${authUser.user.name.value}, welcome to Gospeak!")))
      } yield authenticatorResult)
    } yield result
  }

  def doLogout(): Action[AnyContent] = UserAction { implicit req =>
    authSrv.logout(logoutRedirect)
  }

  def resendEmailValidation(): Action[AnyContent] = UserAction { implicit req =>
    resendEmailValidation(req.user)(req.userAware)
  }

  def resendEmailValidationExt(email: EmailAddress): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      user <- OptionT(userRepo.find(email))
      res <- OptionT.liftF(resendEmailValidation(user))
    } yield res).value.map(_.getOrElse(redirectToPreviousPageOr(routes.AuthCtrl.login()).flashing("error" -> s"No user found with email ${email.value}")))
  }

  private def resendEmailValidation(user: User)(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    for {
      existingValidationOpt <- userRequestRepo.findPendingAccountValidationRequest(user.id, req.now)
      emailValidation <- existingValidationOpt.map(IO.pure).getOrElse(userRequestRepo.createAccountValidationRequest(user.email, user.id, req.now))
      _ <- emailSrv.send(Emails.accountValidation(emailValidation, user))
    } yield redirectToPreviousPageOr(routes.AuthCtrl.login()).flashing("success" -> "Email validation sent!")
  }

  // not UserAction because some user can't connect before validating their email
  def doValidateAccount(id: UserRequest.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    (for {
      validation <- OptionT(userRequestRepo.findAccountValidationRequest(id))
      msg <- if (validation.isPending(req.now)) {
        OptionT.liftF(userRequestRepo.validateAccount(id, validation.email, req.now).map(_ => "success" -> s"Well done! You validated your email.${req.user.map(_ => "").getOrElse(" You can now login!")}"))
      } else if (validation.deadline.isBefore(req.now)) {
        OptionT.liftF(IO.pure("error" -> "Expired deadline for email validation. Please ask to resend the validation email."))
      } else if (validation.acceptedAt.nonEmpty) {
        OptionT.liftF(IO.pure("error" -> s"This validation was already used. Please contact <b>${Constants.Gospeak.contactEmail.address.value}</b> if this is not expected."))
      } else {
        OptionT.liftF(IO.pure("error" -> "Can't validate your email, please ask to resend the validation email."))
      }
      res = Redirect(req.user.map(_ => UserRoutes.index()).getOrElse(routes.AuthCtrl.login())).flashing(msg)
    } yield res).value.map(_.getOrElse(notFound()))
  }

  def forgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    loggedRedirect(IO.pure(Ok(html.forgotPassword(GsForms.forgotPassword, redirect))), redirect)
  }

  def doForgotPassword(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    GsForms.forgotPassword.bindFromRequest.fold(
      formWithErrors => IO.pure(BadRequest(html.forgotPassword(formWithErrors, redirect))),
      data => (for {
        user <- OptionT(userRepo.find(data.email))
        existingPasswordResetOpt <- OptionT.liftF(userRequestRepo.findPendingPasswordResetRequest(data.email, req.now))
        passwordReset <- existingPasswordResetOpt.map(OptionT.pure(_): OptionT[IO, PasswordResetRequest]).getOrElse(OptionT.liftF(userRequestRepo.createPasswordResetRequest(data.email, req.now)))
        _ <- OptionT.liftF(emailSrv.send(Emails.forgotPassword(user, passwordReset)))
      } yield Redirect(routes.AuthCtrl.login()).flashing("success" -> "Reset password email sent!")).value
        .map(_.getOrElse(Redirect(routes.AuthCtrl.forgotPassword(redirect)).flashing("error" -> s"Email not found")))
    )
  }

  def resetPassword(id: UserRequest.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    loggedRedirect(resetPasswordForm(id, GsForms.resetPassword), None)
  }

  def doResetPassword(id: UserRequest.Id): Action[AnyContent] = UserAwareAction { implicit req =>
    GsForms.resetPassword.bindFromRequest.fold(
      formWithErrors => resetPasswordForm(id, formWithErrors),
      data => (for {
        passwordReset <- OptionT(userRequestRepo.findPendingPasswordResetRequest(id))
        user <- OptionT(userRepo.find(passwordReset.email))
        updatedUser <- OptionT.liftF(authSrv.updateIdentity(data, passwordReset, user))
        (_, result) <- OptionT.liftF(authSrv.login(updatedUser, data.rememberMe, loggedRedirect(None)(_)))
      } yield result).value.map(_.getOrElse(notFound()))
    )
  }

  private def resetPasswordForm(id: UserRequest.Id, form: Form[GsForms.ResetPasswordData])(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
    userRequestRepo.findPendingPasswordResetRequest(id).map { passwordResetOpt =>
      passwordResetOpt.map { passwordReset =>
        Ok(html.resetPassword(passwordReset, form))
      }.getOrElse {
        Redirect(routes.AuthCtrl.login()).flashing("error" -> "Reset password request expired, create it again.")
      }
    }
  }
}
