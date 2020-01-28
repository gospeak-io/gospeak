package fr.gospeak.web.auth.domain

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator

trait CookieEnv extends Env {
  type I = AuthUser
  type A = CookieAuthenticator
}
