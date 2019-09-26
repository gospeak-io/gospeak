package fr.gospeak.web.utils

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.api.domain.PublicApiResponse
import fr.gospeak.web.auth.domain.CookieEnv
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Result}

import scala.concurrent.Future

class ApiCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  protected def user(implicit req: SecuredRequest[CookieEnv, AnyContent]): User.Id = req.identity.user.id

  protected def response[A](res: => IO[Option[A]])(implicit w: Writes[A]): Future[Result] = {
    val start = Instant.now()
    res.map(_.map(r => Ok(Json.toJson(PublicApiResponse(r, start)))).getOrElse(NotFound)).unsafeToFuture()
  }

  protected def responsePage[A](res: => IO[Page[A]])(implicit w: Writes[A]): Future[Result] = {
    val start = Instant.now()
    res.map(r => Ok(Json.toJson(PublicApiResponse(r, start)))).unsafeToFuture()
  }

  protected def responsePageT[A](res: => OptionT[IO, Page[A]])(implicit w: Writes[A]): Future[Result] = {
    val start = Instant.now()
    res.value.map(_.map(r => Ok(Json.toJson(PublicApiResponse(r, start)))).getOrElse(NotFound)).unsafeToFuture()
  }
}
