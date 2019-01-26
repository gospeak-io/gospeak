package fr.gospeak.web.auth

import cats.effect.IO
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.{Done, Email}
import fr.gospeak.core.services.GospeakDb
import play.api.mvc.{AnyContent, Request}

// TODO mock auth, to remove
class AuthService(db: GospeakDb) {
  private var logged: Option[User] = db.getUser(Email("demo@mail.com")).unsafeRunSync()

  def login(user: User): IO[Done] = {
    logged = Some(user)
    IO.pure(Done)
  }

  def logout(): IO[Done] = {
    logged = None
    IO.pure(Done)
  }

  def userAware()(implicit req: Request[AnyContent]): Option[User] = {
    req.queryString.get("user")
      .flatMap(_.headOption)
      .map { u => if (u.contains("@")) u else u + "@mail.com" }
      .map(Email)
      .flatMap(db.getUser(_).unsafeRunSync())
      .orElse(logged)
  }

  def authed()(implicit req: Request[AnyContent]): User =
    userAware().get
}
