package fr.gospeak.web.utils

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.core.services.storage.OrgaGroupRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.utils._
import fr.gospeak.web.auth.domain.CookieEnv
import org.h2.jdbc.{JdbcSQLIntegrityConstraintViolationException, JdbcSQLSyntaxErrorException}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc._

import scala.util.control.NonFatal

class ApiCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf) extends AbstractController(cc) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  protected def CustomUserAwareAction[A](bodyParser: BodyParser[A])(block: UserAwareReq[A] => IO[Result]): Action[A] =
    silhouette.UserAwareAction(bodyParser).async { r =>
      val req = UserAwareReq.from(conf, messagesApi, r)
      recoverFailedAction(block(req))(req).unsafeToFuture()
    }

  protected def CustomUserAwareAction(block: UserAwareReq[AnyContent] => IO[Result]): Action[AnyContent] =
    CustomUserAwareAction(parse.anyContent)(block)

  protected def UserAwareAction[P, R](bodyParser: BodyParser[P])(block: UserAwareReq[P] => IO[ApiResponse[R]])(implicit w: Writes[R]): Action[P] =
    CustomUserAwareAction(bodyParser)(block(_).map(asResult(_)))

  protected def UserAwareAction[R](block: UserAwareReq[AnyContent] => IO[ApiResponse[R]])(implicit w: Writes[R]): Action[AnyContent] =
    CustomUserAwareAction(parse.anyContent)(block(_).map(asResult(_)))


  // FIXME avoid usage of this
  protected def CustomUserAction[P](bodyParser: BodyParser[P])(block: UserReq[P] => IO[Result]): Action[P] =
    silhouette.SecuredAction(bodyParser).async { r =>
      val req = UserReq.from(conf, messagesApi, r)
      recoverFailedAction(block(req))(req).unsafeToFuture()
    }

  // FIXME avoid usage of this
  protected def CustomUserAction(block: UserReq[AnyContent] => IO[Result]): Action[AnyContent] =
    CustomUserAction(parse.anyContent)(block)

  protected def UserAction[P, R](bodyParser: BodyParser[P])(block: UserReq[P] => IO[ApiResponse[R]])(implicit w: Writes[R]): Action[P] =
    CustomUserAction(bodyParser)(block(_).map(asResult(_)))

  protected def UserActionJson[P, R](block: UserReq[P] => IO[ApiResponse[R]])(implicit r: Reads[P], w: Writes[R]): Action[JsValue] =
    CustomUserAction(parse.json) { implicit req =>
      req.body.validate[P].fold(
        errors => IO.pure(ApiResponse.badRequest(errors)),
        data => block(req.withBody(data))
      ).map(asResult(_))
    }

  protected def UserAction[R](block: UserReq[AnyContent] => IO[ApiResponse[R]])(implicit w: Writes[R]): Action[AnyContent] =
    CustomUserAction(parse.anyContent)(block(_).map(asResult(_)))


  private def recoverFailedAction[P](result: IO[Result])(implicit req: BasicReq[P]): IO[Result] = {
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

  private def asResult[R](r: ApiResponse[R])(implicit w: Writes[R]): Result = r match {
    case i: ItemResponse[R] => Ok(Json.toJson(i))
    case p: PageResponse[R] => Ok(Json.toJson(p))
    case e: ErrorResponse => new Status(e.status)(Json.toJson(e))
  }

  protected def groupNotFound(group: Group.Slug)(implicit ctx: BasicCtx): ErrorResponse =
    ApiResponse.notFound(s"No group '${group.value}'")

  protected def eventNotFound(group: Group.Slug, event: Event.Slug)(implicit ctx: BasicCtx): ErrorResponse =
    ApiResponse.notFound(s"No event '${event.value}' in group '${group.value}'")

  protected def talkNotFound(group: Group.Slug, talk: Proposal.Id)(implicit ctx: BasicCtx): ErrorResponse =
    ApiResponse.notFound(s"No talk '${talk.value}' in group '${group.value}'")

  protected def cfpNotFound(cfp: Cfp.Slug)(implicit ctx: BasicCtx): ErrorResponse =
    ApiResponse.notFound(s"No cfp '${cfp.value}'")

  protected def userNotFound(user: User.Slug)(implicit ctx: BasicCtx): ErrorResponse =
    ApiResponse.notFound(s"No user '${user.value}'")
}

object ApiCtrl {

  trait OrgaAction {
    self: ApiCtrl =>
    val groupRepo: OrgaGroupRepo

    protected def OrgaAction[R](group: Group.Slug)(block: OrgaReq[AnyContent] => IO[ApiResponse[R]])(implicit w: Writes[R]): Action[AnyContent] = {
      CustomUserAction { implicit req =>
        groupRepo.find(group).flatMap {
          case Some(groupElt) if groupElt.owners.toList.contains(req.user.id) => block(req.orga(groupElt)).map(asResult(_))
          case Some(_) => IO.pure(Forbidden(Json.toJson(ApiResponse.forbidden(s"You are not a '${group.value}' group owner")(req))))
          case None => IO.pure(NotFound(Json.toJson(groupNotFound(group))))
        }
      }
    }
  }

}
