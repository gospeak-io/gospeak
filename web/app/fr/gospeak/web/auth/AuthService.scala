package fr.gospeak.web.auth

import cats.effect.IO
import fr.gospeak.core.domain.User
import fr.gospeak.core.services.GospeakDb
import fr.gospeak.libs.scalautils.domain.{Done, Email}
import play.api.mvc.{AnyContent, Request}

import scala.util.Try

// TODO mock auth, to remove
class AuthService(db: GospeakDb) {
  private var logged: Option[User] = Try(db.getUser(Email.from("demo@mail.com").right.get).unsafeRunSync()).getOrElse(None)

  def login(user: User): IO[Done] = {
    logged = Some(user)
    IO.pure(Done)
  }

  def logout(): IO[Done] = {
    logged = None
    IO.pure(Done)
  }

  def userAware()(implicit req: Request[AnyContent]): Option[User] = {
    val user = req.queryString.get("user")
      .flatMap(_.headOption)
      .map { u => if (u.contains("@")) u else u + "@mail.com" }
      .map(Email.from(_).right.get)
      .flatMap(db.getUser(_).unsafeRunSync())
    user.foreach(login(_).unsafeRunSync())
    user.orElse(logged)
  }

  def authed()(implicit req: Request[AnyContent]): User =
    userAware().get
}
