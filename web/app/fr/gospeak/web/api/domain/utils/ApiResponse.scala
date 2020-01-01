package fr.gospeak.web.api.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.utils.BasicCtx
import fr.gospeak.libs.scalautils.domain.Page
import play.api.libs.json.{Json, Writes}

sealed trait ApiResponse[A] {
  val data: A
  val execMs: Long
}

object ApiResponse {
  def from[A, B](p: Page[A], f: A => B)(implicit ctx: BasicCtx): PageResponse[Seq[B]] =
    PageResponse[Seq[B]](
      data = p.items.map(f),
      totalItems = p.total.value,
      pageSize = p.params.pageSize.value,
      pageNo = p.params.page.value,
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

final case class ErrorResponse(data: Int, // HTTP status code
                               message: String,
                               execMs: Long) extends ApiResponse[Int]

object ErrorResponse {
  implicit val writes: Writes[ErrorResponse] = Json.writes[ErrorResponse]
}
