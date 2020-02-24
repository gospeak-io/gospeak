package gospeak.web.auth.services

import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import gospeak.web.pages.user.routes.UserCtrl
import gospeak.web.utils.HttpUtils
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results.Redirect
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class CustomUnsecuredErrorHandler(val messagesApi: MessagesApi) extends UnsecuredErrorHandler with I18nSupport {
  override def onNotAuthorized(implicit req: RequestHeader): Future[Result] = {
    val next = Redirect(HttpUtils.getReferer(req.headers).getOrElse(UserCtrl.index().path()))
    Future.successful(next.flashing("error" -> "Forbidden access"))
  }
}
