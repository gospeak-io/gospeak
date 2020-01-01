package fr.gospeak.web.api.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.libs.scalautils.domain.Page
import play.api.http.Status
import play.api.libs.json.{Json, Writes}

sealed trait ApiResponse[+A] {
  def data: A

  val execMs: Long
}

object ApiResponse {
  def from[A](p: A)(implicit ctx: BasicCtx): ItemResponse[A] =
    ItemResponse[A](
      data = p,
      execMs = Instant.now().toEpochMilli - ctx.now.toEpochMilli)

  def from[A, B](p: Page[A], f: A => B)(implicit ctx: BasicCtx): PageResponse[Seq[B]] =
    PageResponse[Seq[B]](
      data = p.items.map(f),
      totalItems = p.total.value,
      pageSize = p.params.pageSize.value,
      pageNo = p.params.page.value,
      execMs = Instant.now().toEpochMilli - ctx.now.toEpochMilli)

  def notFound(message: String)(implicit ctx: BasicCtx): ErrorResponse = err(Status.NOT_FOUND, message)

  def forbidden(message: String)(implicit ctx: BasicCtx): ErrorResponse = err(Status.FORBIDDEN, message)

  private def err(status: Int, message: String)(implicit ctx: BasicCtx): ErrorResponse =
    ErrorResponse(
      status = status,
      message = message,
      execMs = Instant.now().toEpochMilli - ctx.now.toEpochMilli)
}

final case class ItemResponse[A](data: A,
                                 execMs: Long) extends ApiResponse[A]

object ItemResponse {
  implicit def writes[A](implicit a: Writes[A]): Writes[ItemResponse[A]] = Json.writes[ItemResponse[A]]
}

final case class PageResponse[A](data: A,
                                 totalItems: Long,
                                 pageSize: Int,
                                 pageNo: Int,
                                 execMs: Long) extends ApiResponse[A]

object PageResponse {
  implicit def writes[A](implicit a: Writes[A]): Writes[PageResponse[A]] = Json.writes[PageResponse[A]]
}

final case class ErrorResponse(status: Int,
                               message: String,
                               execMs: Long) extends ApiResponse[Nothing] {
  override def data: Nothing = throw new NoSuchElementException("ErrorResponse.data")
}

object ErrorResponse {
  implicit val writes: Writes[ErrorResponse] = Json.writes[ErrorResponse]
}
