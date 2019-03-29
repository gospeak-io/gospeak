package fr.gospeak.web.auth

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest.EmailValidationRequest
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthCtrl._
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.auth.exceptions.{DuplicateIdentityException, DuplicateSlugException}
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.utils.UICtrl
import play.api.i18n.Messages
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

// TODO Add rememberMe feature
// TODO Add endpoint to send email validation
// TODO Signup email template
// TODO Password recovery
// TODO Test this controller
// TODO JWT Auth for API
class AuthCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               db: GospeakDb,
               authSrv: AuthSrv,
               emailSrv: EmailSrv) extends UICtrl(cc, silhouette) {
  private def loginRedirect(redirect: Option[String]): Result = Redirect(redirect.getOrElse(fr.gospeak.web.user.routes.UserCtrl.index().path()))
  private val logoutRedirect = Redirect(fr.gospeak.web.routes.HomeCtrl.index())

  import silhouette._

  def signup(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.signup(AuthForms.signup, redirect)(header)))
  }

  def doSignup(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    println("doSignup")
    val now = Instant.now()
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.signup(formWithErrors, redirect)(header))),
      data => (for {
        user <- authSrv.createIdentity(data, now).unsafeToFuture()
        emailValidation <- db.createEmailValidationRequest(user.user.email, user.user.id, now).unsafeToFuture()
        _ <- sendSignupEmail(user, emailValidation).unsafeToFuture()
        result <- authSrv.login(user, loginRedirect(redirect))
      } yield result).recover {
        case _: DuplicateIdentityException => BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError("User already exists"), redirect)(header))
        case e: DuplicateSlugException => BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"Username ${e.slug.value} is already taken"), redirect)(header))
        case NonFatal(e) => BadRequest(html.signup(AuthForms.signup.fill(data).withGlobalError(s"${e.getClass.getSimpleName}: ${e.getMessage}"), redirect)(header))
      }
    )
  }

  private def sendSignupEmail(user: AuthUser, emailValidation: EmailValidationRequest)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): IO[Unit] = {
    import EmailSrv._
    val email = Email(
      from = Contact("noreply@gospeak.fr", Some("Gospeak")),
      to = Seq(Contact(user.user.email.value, Some(user.user.name.value))),
      subject = "Welcome to gospeak!",
      content = HtmlContent(emails.html.signup(user, emailValidation).body)
    )
    emailSrv.send(email)
  }

  def login(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.login(AuthForms.login, redirect)(header)))
  }

  def doLogin(redirect: Option[String]): Action[AnyContent] = UserAwareAction.async { implicit req =>
    println("doLogin")
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(html.login(formWithErrors, redirect)(header))),
      data => (for {
        user <- authSrv.getIdentity(data)
        result <- authSrv.login(user, loginRedirect(redirect))
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

  def doValidateEmail(id: UserRequest.Id): Action[AnyContent] = UserAwareAction.async { implicit req =>
    println("doValidateEmail")
    val now = Instant.now()
    (for {
      validation <- OptionT(db.getPendingEmailValidationRequest(id, now))
      _ <- OptionT.liftF(db.validateEmail(id, validation.user, now))
    } yield Redirect(routes.AuthCtrl.login()).flashing("success" -> s"Well done! You validated your email.")).value.map(_.getOrElse(notFound())).unsafeToFuture()
  }

  def passwordReset(redirect: Option[String]): Action[AnyContent] = UserAwareAction { implicit req =>
    req.identity
      .map(_ => loginRedirect(redirect).flashing(req.flash))
      .getOrElse(Ok(html.passwordReset(AuthForms.passwordReset, redirect)(header)))
  }

  def doPasswordReset(redirect: Option[String]): Action[AnyContent] = UnsecuredAction { implicit req =>
    // TODO
    Redirect(routes.AuthCtrl.login())
  }
}

object AuthCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.AuthCtrl.login())
}
