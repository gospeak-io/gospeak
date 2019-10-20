package fr.gospeak.web.auth

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.services.AuthSrv
import fr.gospeak.web.pages
import fr.gospeak.web.utils.UICtrl
import org.apache.http.auth.AuthenticationException
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class SocialAuthCtrl(cc: ControllerComponents,
                     silhouette: Silhouette[CookieEnv],
                     val authSrv: AuthSrv)
  extends UICtrl(cc, silhouette) {

  def authenticate(providerID: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      provider <- authSrv.provider(providerID).toFuture(new AuthenticationException(s"Cannot authenticate with unexpected social provider $providerID"))
      result <- provider.authenticate().flatMap {
        case Left(result) => Future.successful(result)
        case Right(authInfo) =>
          for {
            profile <- provider.retrieveProfile(authInfo)
            socialProfile <- Try(profile.asInstanceOf[CommonSocialProfile]).toFuture
            authUser <- authSrv.createOrEdit(socialProfile).unsafeToFuture()
            redirect = Redirect(pages.user.routes.UserCtrl.index()).flashing("success" -> s"Well done! You are now authenticated.")
            authenticatorResult <- authSrv.login(authUser, rememberMe = true, redirect)
          } yield authenticatorResult
      }
    } yield result
  }
}


