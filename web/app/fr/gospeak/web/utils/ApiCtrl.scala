package fr.gospeak.web.utils

import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.User
import fr.gospeak.web.auth.domain.CookieEnv
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents}

class ApiCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  protected def user(implicit req: SecuredRequest[CookieEnv, AnyContent]): User.Id = req.identity.user.id
}
