package fr.gospeak.web.auth.domain

import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import fr.gospeak.core.domain.User

case class AuthUser(loginInfo: LoginInfo, user: User) extends Identity
