package fr.gospeak.web.auth

import cats.effect.IO
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthCtrl._
import fr.gospeak.web.domain.HeaderInfo
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

class AuthCtrl(cc: ControllerComponents, db: GospeakDb, auth: AuthService) extends UICtrl(cc) {
  def signup(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    auth.userAware() match {
      case Some(_) => Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
      case None => Ok(html.signup(AuthForms.signup)(header))
    }
  }

  def doSignup(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => IO.pure(Ok(html.signup(formWithErrors)(header))),
      data => for {
        user <- db.createUser(data.firstName, data.lastName, data.email)
        _ <- auth.login(user)
      } yield Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
    ).unsafeToFuture()
  }

  def login(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    auth.userAware() match {
      case Some(_) => Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
      case None => Ok(html.login(AuthForms.login)(header))
    }
  }

  def doLogin(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => IO.pure(Ok(html.login(formWithErrors)(header))),
      data => db.getUser(data.email).flatMap {
        case Some(user) => auth.login(user).map(_ => Redirect(fr.gospeak.web.user.routes.UserCtrl.index()))
        case None => IO.pure(Ok(html.login(AuthForms.login.fill(data))(header)))
      }
    ).unsafeToFuture()
  }

  def passwordReset(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    auth.userAware() match {
      case Some(_) => Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
      case None => Ok(html.passwordReset(AuthForms.passwordReset)(header))
    }
  }

  def doPasswordReset(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Redirect(routes.AuthCtrl.login())
  }

  def logout(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    auth.logout().map { _ =>
      Redirect(fr.gospeak.web.routes.HomeCtrl.index())
    }.unsafeToFuture()
  }
}

object AuthCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.AuthCtrl.login())
}
