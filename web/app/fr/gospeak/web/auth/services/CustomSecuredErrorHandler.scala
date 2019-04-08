package fr.gospeak.web.auth.services

import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import fr.gospeak.web.auth.routes.AuthCtrl
import fr.gospeak.web.pages.user.routes.UserCtrl
import fr.gospeak.web.utils.HttpUtils
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class CustomSecuredErrorHandler(val messagesApi: MessagesApi) extends SecuredErrorHandler with I18nSupport {
  override def onNotAuthenticated(implicit req: RequestHeader): Future[Result] = {
    Future.successful(Redirect(AuthCtrl.login(Some(req.uri))).flashing("warning" -> "Authentication needed"))
  }

  override def onNotAuthorized(implicit req: RequestHeader): Future[Result] = {
    val url = HttpUtils.getReferer(req.headers).getOrElse(UserCtrl.index().path())
    Future.successful(Redirect(url).flashing("error" -> "Forbidden access"))
  }
}
