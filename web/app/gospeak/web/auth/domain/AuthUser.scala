package gospeak.web.auth.domain

import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import gospeak.core.domain.{Group, User}

case class AuthUser(loginInfo: LoginInfo, user: User, groups: List[Group]) extends Identity {
  def shouldValidateEmail(): Boolean = user.emailValidationBeforeLogin && user.emailValidated.isEmpty
}
