package fr.gospeak.web.utils

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.storage.OrgaGroupRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.utils.{ApiResponse, ErrorResponse, ItemResponse, PageResponse, PublicApiError, PublicApiResponse}
import fr.gospeak.web.auth.domain.CookieEnv
import org.h2.jdbc.{JdbcSQLIntegrityConstraintViolationException, JdbcSQLSyntaxErrorException}
import org.slf4j.LoggerFactory
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import scala.util.control.NonFatal

class ApiCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf) extends AbstractController(cc) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  protected def ActionIO[A](bodyParser: BodyParser[A])(block: BasicReq[A] => IO[Result]): Action[A] = Action(bodyParser).async { r =>
    block(BasicReq.from(conf, messagesApi, r))
      .recover { case NonFatal(e) => InternalServerError(Json.toJson(PublicApiError(e.getMessage))) }.unsafeToFuture()
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

  /*
    New methods
   */


  protected def UserAction[A](bodyParser: BodyParser[A])(block: UserReq[A] => IO[Result]): Action[A] = silhouette.SecuredAction(bodyParser).async { r =>
    val req = UserReq.from(conf, messagesApi, r)
    recoverFailedAction(block(UserReq.from(conf, messagesApi, r)))(req).unsafeToFuture()
  }

  protected def UserAction(block: UserReq[AnyContent] => IO[Result]): Action[AnyContent] = UserAction(parse.anyContent)(block)

  private def recoverFailedAction[A](result: IO[Result])(implicit req: BasicReq[A]): IO[Result] = {
    def logError(e: Throwable): Unit = {
      val (user, group) = req match {
        case r: OrgaReq[_] => Some(r.user) -> Some(r.group)
        case r: UserReq[_] => Some(r.user) -> None
        case r: UserAwareReq[_] => r.user -> None
        case _: BasicReq[_] => None -> None
      }
      val userStr = user.map(u => s" for user ${u.name.value} (${u.id.value})").getOrElse("")
      val groupStr = group.map(g => s" in group ${g.name.value} (${g.id.value})").getOrElse("")
      logger.error("Error in controller" + userStr + groupStr, e)
    }

    // FIXME better error handling (send email or notif?)
    result.recover {
      case e: JdbcSQLSyntaxErrorException => logError(e); InternalServerError(s"Unexpected SQL error")
      case e: JdbcSQLIntegrityConstraintViolationException => logError(e); InternalServerError(s"Duplicate key SQL error")
      case NonFatal(e) => logError(e); InternalServerError(s"Unexpected error: ${e.getMessage} (${e.getClass.getSimpleName})")
      case NonFatal(e) => logError(e); InternalServerError(Json.toJson(PublicApiError(e.getMessage)))
    }
  }
}

object ApiCtrl {

  trait OrgaAction {
    self: ApiCtrl =>
    val groupRepo: OrgaGroupRepo

    protected def OrgaAction[A](group: Group.Slug)(block: OrgaReq[AnyContent] => IO[ApiResponse[A]])(implicit w: Writes[A]): Action[AnyContent] = {
      UserAction { req =>
        groupRepo.find(req.user.id, group).flatMap {
          case Some(group) => block(req.orga(group)).map{
            case i: ItemResponse[A] => Ok(Json.toJson(i))
            case p: PageResponse[A] => Ok(Json.toJson(p))
            case e: ErrorResponse => new Status(e.data)(Json.toJson(e))
          }
          case None => IO.pure(NotFound(s"Unable to find group with slug '${group.value}'"))
        }
      }
    }
  }

}
