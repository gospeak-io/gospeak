package fr.gospeak.web.auth

import fr.gospeak.core.services.GospeakDb
import fr.gospeak.web.HomeCtrl
import fr.gospeak.web.auth.AuthCtrl._
import fr.gospeak.web.domain.HeaderInfo
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthCtrl(cc: ControllerComponents, db: GospeakDb) extends AbstractController(cc) {
  def signup(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    db.userAware() match {
      case Some(_) => Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
      case None => Ok(html.signup(AuthForms.signup)(header))
    }
  }

  def doSignup(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    AuthForms.signup.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(html.signup(formWithErrors)(header))),
      data => for {
        user <- db.createUser(data.firstName, data.lastName, data.email)
        _ <- db.setLogged(user)
      } yield Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
    )
  }

  def login(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    db.userAware() match {
      case Some(_) => Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
      case None => Ok(html.login(AuthForms.login)(header))
    }
  }

  def doLogin(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    AuthForms.login.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(html.login(formWithErrors)(header))),
      data => db.getUser(data.email).flatMap {
        case Some(user) => db.setLogged(user).map(_ => Redirect(fr.gospeak.web.user.routes.UserCtrl.index()))
        case None => Future.successful(Ok(html.login(AuthForms.login.fill(data))(header)))
      }
    )
  }

  def passwordReset(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    db.userAware() match {
      case Some(_) => Redirect(fr.gospeak.web.user.routes.UserCtrl.index())
      case None => Ok(html.passwordReset(AuthForms.passwordReset)(header))
    }
  }

  def doPasswordReset(): Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Redirect(routes.AuthCtrl.login())
  }

  def logout(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    db.logout().map { _ =>
      Redirect(fr.gospeak.web.routes.HomeCtrl.index())
    }
  }
}

object AuthCtrl {
  val header: HeaderInfo =
    HomeCtrl.header.activeFor(routes.AuthCtrl.login())
}
