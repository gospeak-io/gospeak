package gospeak.web.auth.domain

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator

trait JwtEnv extends Env {
  type I = AuthUser
  type A = JWTAuthenticator
}
