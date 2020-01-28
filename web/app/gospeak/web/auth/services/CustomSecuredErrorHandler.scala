package fr.gospeak.web.auth.services

import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import fr.gospeak.web.api.domain.utils.ErrorResult
import fr.gospeak.web.auth.routes.AuthCtrl
import fr.gospeak.web.pages.user.routes.UserCtrl
import fr.gospeak.web.utils.HttpUtils
import play.api.http.Status
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class CustomSecuredErrorHandler(val messagesApi: MessagesApi) extends SecuredErrorHandler with I18nSupport {
  override def onNotAuthenticated(implicit req: RequestHeader): Future[Result] = {
    val message = "Unauthorized: please login"
    if (req.uri.startsWith("/api")) {
      Future.successful(Unauthorized(Json.toJson(ErrorResult(Status.UNAUTHORIZED, message, execMs = 0))))
    } else {
      Future.successful(Redirect(AuthCtrl.login(Some(req.uri))).flashing("warning" -> message))
    }
  }

  override def onNotAuthorized(implicit req: RequestHeader): Future[Result] = {
    val message = "Forbidden: it seems you lack some rights here"
    if (req.uri.startsWith("/api")) {
      Future.successful(Unauthorized(Json.toJson(ErrorResult(Status.FORBIDDEN, message, execMs = 0))))
    } else {
      val next = Redirect(HttpUtils.getReferer(req.headers).getOrElse(UserCtrl.index().path()))
      Future.successful(next.flashing("error" -> message))
    }
  }
}
