package gospeak.web.utils

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.domain.utils.BasicCtx
import gospeak.core.services.storage.OrgaGroupRepo
import gospeak.libs.scala.Extensions._
import gospeak.web.AppConf
import gospeak.web.api.domain.utils._
import gospeak.web.auth.domain.CookieEnv
import org.h2.jdbc.{JdbcSQLIntegrityConstraintViolationException, JdbcSQLSyntaxErrorException}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc._

import scala.concurrent.Future
import scala.util.control.NonFatal

class ApiCtrl(cc: ControllerComponents,
              silhouette: Silhouette[CookieEnv],
              conf: AppConf) extends AbstractController(cc) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def UserAwareAction[P, R](bodyParser: BodyParser[P])(block: UserAwareReq[P] => IO[ApiResult[R]])(implicit w: Writes[R]): Action[P] =
    silhouette.UserAwareAction(bodyParser).async { r =>
      implicit val req: UserAwareReq[P] = UserAwareReq.from(conf, messagesApi, r)
      actionResult(block(req))
    }

  protected def UserAwareAction[R](block: UserAwareReq[AnyContent] => IO[ApiResult[R]])(implicit w: Writes[R]): Action[AnyContent] =
    UserAwareAction(parse.anyContent)(block)

  protected def UserAwareActionJson[P, R](block: UserAwareReq[P] => IO[ApiResult[R]])(implicit r: Reads[P], w: Writes[R]): Action[JsValue] =
    UserAwareAction(parse.json) { implicit req =>
      req.body.validate[P].fold(
        errors => IO.pure(ApiResult.badRequest(errors)),
        data => block(req.withBody(data))
      )
    }


  private def UserAction[P, R](bodyParser: BodyParser[P])(block: UserReq[P] => IO[ApiResult[R]])(implicit w: Writes[R]): Action[P] =
    silhouette.SecuredAction(bodyParser).async { r =>
      implicit val req: UserReq[P] = UserReq.from(conf, messagesApi, r)
      actionResult(block(req))
    }

  protected def UserAction[R](block: UserReq[AnyContent] => IO[ApiResult[R]])(implicit w: Writes[R]): Action[AnyContent] =
    UserAction(parse.anyContent)(block(_))

  protected def UserActionJson[P, R](block: UserReq[P] => IO[ApiResult[R]])(implicit r: Reads[P], w: Writes[R]): Action[JsValue] =
    UserAction(parse.json) { implicit req =>
      req.body.validate[P].fold(
        errors => IO.pure(ApiResult.badRequest(errors)),
        data => block(req.withBody(data))
      )
    }


  private def actionResult[P, R](result: IO[ApiResult[R]])(implicit req: BasicReq[P], w: Writes[R]): Future[Result] = {
    def logError(e: Throwable): Unit = {
      val (user, group) = req match {
        case r: OrgaReq[_] => Some(r.user) -> Some(r.group)
        case r: UserReq[_] => Some(r.user) -> None
        case r: UserAwareReq[_] => r.user -> None
      }
      val userStr = user.map(u => s" for user ${u.name.value} (${u.id.value})").getOrElse("")
      val groupStr = group.map(g => s" in group ${g.name.value} (${g.id.value})").getOrElse("")
      logger.error("Error in controller" + userStr + groupStr, e) // FIXME better error handling (send email or notif?)
    }

    result.recover {
      case e: JdbcSQLSyntaxErrorException => logError(e); ApiResult.internalServerError(s"Unexpected SQL error")
      case e: JdbcSQLIntegrityConstraintViolationException => logError(e); ApiResult.internalServerError(s"Duplicate key SQL error")
      case NonFatal(e) => logError(e); ApiResult.internalServerError(s"Unexpected error: ${e.getMessage} (${e.getClass.getSimpleName})")
    }.map {
      case i: ItemResult[R] => Ok(Json.toJson(i))
      case p: PageResult[R] => Ok(Json.toJson(p))
      case e: ErrorResult => new Status(e.status)(Json.toJson(e))
    }.unsafeToFuture()
  }

  protected def groupNotFound(group: Group.Slug)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No group '${group.value}'")

  protected def eventNotFound(group: Group.Slug, event: Event.Slug)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No event '${event.value}' in group '${group.value}'")

  protected def talkNotFound(group: Group.Slug, talk: Proposal.Id)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No talk '${talk.value}' in group '${group.value}'")

  protected def cfpNotFound(cfp: Cfp.Slug)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No cfp '${cfp.value}'")

  protected def cfpNotFound(cfp: ExternalCfp.Id)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No external cfp with id '${cfp.value}'")

  protected def proposalNotFound(cfp: Cfp.Slug, proposal: Proposal.Id)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No proposal with id '${proposal.value}' in cfp '${cfp.value}'")

  protected def userNotFound(user: User.Slug)(implicit ctx: BasicCtx): ErrorResult =
    ApiResult.notFound(s"No user '${user.value}'")
}

object ApiCtrl {

  trait OrgaAction {
    self: ApiCtrl =>
    val groupRepo: OrgaGroupRepo

    protected def OrgaAction[R](group: Group.Slug)(block: OrgaReq[AnyContent] => IO[ApiResult[R]])(implicit w: Writes[R]): Action[AnyContent] = {
      UserAction { implicit req =>
        groupRepo.find(group).flatMap {
          case Some(groupElt) if groupElt.owners.toList.contains(req.user.id) => block(req.orga(groupElt))
          case Some(_) => IO.pure(ApiResult.forbidden(s"You are not a '${group.value}' group owner"))
          case None => IO.pure(groupNotFound(group))
        }
      }
    }
  }

  trait AdminAction {
    self: ApiCtrl =>

    protected def AdminAction[R](block: AdminReq[AnyContent] => IO[ApiResult[R]])(implicit w: Writes[R]): Action[AnyContent] = {
      UserAction { implicit req =>
        if (req.isAdmin) block(req.admin) else IO.pure(ApiResult.forbidden(s"You are not an admin"))
      }
    }
  }

}
