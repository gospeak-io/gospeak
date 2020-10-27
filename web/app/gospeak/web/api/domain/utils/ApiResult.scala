package gospeak.web.api.domain.utils

import java.time.Instant

import gospeak.core.domain.utils.BasicCtx
import gospeak.web.utils.BasicReq
import gospeak.libs.scala.domain.Page
import play.api.http.Status
import play.api.libs.json._

sealed trait ApiResult[+A] extends Product with Serializable {
  def data: A

  val execMs: Long
}

object ApiResult {
  def of[A](p: A)(implicit ctx: BasicCtx): ItemResult[A] =
    ItemResult[A](
      data = p,
      execMs = Instant.now().toEpochMilli - ctx.now.toEpochMilli)

  def of[A, B](p: Page[A], f: A => B)(implicit ctx: BasicCtx): PageResult[List[B]] =
    PageResult[List[B]](
      data = p.items.map(f),
      totalItems = p.total.value,
      pageSize = p.params.pageSize.value,
      pageNo = p.params.page.value,
      execMs = Instant.now().toEpochMilli - ctx.now.toEpochMilli)

  def notFound(message: String)(implicit ctx: BasicCtx): ErrorResult = err(Status.NOT_FOUND, message)

  def badRequest(message: String)(implicit ctx: BasicCtx): ErrorResult = err(Status.BAD_REQUEST, message)

  def badRequest(errors: Seq[(JsPath, Seq[JsonValidationError])])(implicit req: BasicReq[JsValue]): ErrorResult =
    badRequest("Invalid request body:" + errors.map { case (path, errs) => s"\n  - ${path.toJsonString}: ${errs.map(req.format).mkString(", ")}" }.mkString)

  def forbidden(message: String)(implicit ctx: BasicCtx): ErrorResult = err(Status.FORBIDDEN, message)

  def internalServerError(message: String)(implicit ctx: BasicCtx): ErrorResult = err(Status.INTERNAL_SERVER_ERROR, message)

  private def err(status: Int, message: String)(implicit ctx: BasicCtx): ErrorResult =
    ErrorResult(
      status = status,
      message = message,
      execMs = Instant.now().toEpochMilli - ctx.now.toEpochMilli)
}

final case class ItemResult[A](data: A,
                               execMs: Long) extends ApiResult[A]

object ItemResult {
  implicit def writes[A](implicit a: Writes[A]): Writes[ItemResult[A]] = Json.writes[ItemResult[A]]
}

final case class PageResult[A](data: A,
                               totalItems: Long,
                               pageSize: Int,
                               pageNo: Int,
                               execMs: Long) extends ApiResult[A]

object PageResult {
  implicit def writes[A](implicit a: Writes[A]): Writes[PageResult[A]] = Json.writes[PageResult[A]]
}

final case class ErrorResult(status: Int,
                             message: String,
                             execMs: Long) extends ApiResult[Nothing] {
  override def data: Nothing = throw new NoSuchElementException("ErrorResponse.data")
}

object ErrorResult {
  implicit val writes: Writes[ErrorResult] = Json.writes[ErrorResult]
}
