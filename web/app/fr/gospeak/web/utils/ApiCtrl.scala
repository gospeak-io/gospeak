package fr.gospeak.web.utils

import cats.data.OptionT
import cats.effect.IO
import fr.gospeak.core.ApplicationConf
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.api.domain.utils.{PublicApiError, PublicApiResponse}
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import scala.util.control.NonFatal

class ApiCtrl(cc: ControllerComponents, env: ApplicationConf.Env) extends AbstractController(cc) {
  protected def ActionIO[A](bodyParser: BodyParser[A])(block: BasicReq[A] => IO[Result]): Action[A] = Action(bodyParser).async { req =>
    block(BasicReq[A](req, messagesApi.preferred(req), env)).recover {
      case NonFatal(e) => InternalServerError(Json.toJson(PublicApiError(e.getMessage)))
    }.unsafeToFuture()
  }

  protected def ActionIO(block: BasicReq[AnyContent] => IO[Result]): Action[AnyContent] =
    ActionIO(parse.anyContent)(block)


  protected def ApiAction[R, A](bodyParser: BodyParser[R])(block: BasicReq[R] => IO[PublicApiResponse[A]])(implicit w: Writes[A]): Action[R] =
    ActionIO(bodyParser)(req => block(req).map(okResult(_)))

  protected def ApiAction[A](block: BasicReq[AnyContent] => IO[PublicApiResponse[A]])(implicit w: Writes[A]): Action[AnyContent] =
    ApiAction(parse.anyContent)(block)


  protected def ApiActionOpt[R, A](bodyParser: BodyParser[R])(block: BasicReq[R] => IO[Option[A]])(implicit w: Writes[A]): Action[R] =
    ActionIO(bodyParser)(req => block(req).map(_.map(b => okResult(PublicApiResponse(b, req.now))).getOrElse(NotFound)))

  protected def ApiActionOpt[A](block: BasicReq[AnyContent] => IO[Option[A]])(implicit w: Writes[A]): Action[AnyContent] =
    ApiActionOpt(parse.anyContent)(block)


  protected def ApiActionPage[R, A](bodyParser: BodyParser[R])(block: BasicReq[R] => IO[Page[A]])(implicit w: Writes[A]): Action[R] =
    ApiAction(bodyParser)(req => block(req).map(p => PublicApiResponse(p, req.now)))

  protected def ApiActionPage[A](block: BasicReq[AnyContent] => IO[Page[A]])(implicit w: Writes[A]): Action[AnyContent] =
    ApiActionPage(parse.anyContent)(block)


  protected def ApiActionOptT[R, A](bodyParser: BodyParser[R])(block: BasicReq[R] => OptionT[IO, A])(implicit w: Writes[A]): Action[R] =
    ApiActionOpt(bodyParser)(block(_).value)

  protected def ApiActionOptT[A](block: BasicReq[AnyContent] => OptionT[IO, A])(implicit w: Writes[A]): Action[AnyContent] =
    ApiActionOptT(parse.anyContent)(block)


  protected def ApiActionPageT[R, A](bodyParser: BodyParser[R])(block: BasicReq[R] => OptionT[IO, Page[A]])(implicit w: Writes[A]): Action[R] =
    ActionIO(bodyParser)(req => block(req).value.map(_.map(b => okResult(PublicApiResponse(b, req.now))).getOrElse(NotFound)))

  protected def ApiActionPageT[A](block: BasicReq[AnyContent] => OptionT[IO, Page[A]])(implicit w: Writes[A]): Action[AnyContent] =
    ApiActionPageT(parse.anyContent)(block)

  private def okResult[A](r: PublicApiResponse[A])(implicit w: Writes[A]) = Ok(Json.toJson(r))
}
