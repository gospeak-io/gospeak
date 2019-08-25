package fr.gospeak.web.auth.domain

import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import fr.gospeak.core.domain.{Group, User}

case class AuthUser(loginInfo: LoginInfo, user: User, groups: Seq[Group]) extends Identity {
  def shouldValidateEmail(): Boolean = user.emailValidated.isEmpty && user.created != user.updated
}
