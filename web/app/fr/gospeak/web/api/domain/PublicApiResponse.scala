package fr.gospeak.web.api.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain.Page
import play.api.libs.json.{Json, Writes}

case class PublicApiResponse[A](data: A,
                                totalItems: Option[Long],
                                pageSize: Option[Int],
                                pageNo: Option[Int],
                                execMs: Long)

object PublicApiResponse {
  def apply[A](page: Page[A], start: Instant): PublicApiResponse[Seq[A]] =
    new PublicApiResponse(
      data = page.items,
      totalItems = Some(page.total.value),
      pageSize = Some(page.params.pageSize.value),
      pageNo = Some(page.params.page.value),
      execMs = Instant.now().toEpochMilli - start.toEpochMilli)

  def apply[A](data: A, start: Instant): PublicApiResponse[A] =
    new PublicApiResponse(
      data = data,
      totalItems = None,
      pageSize = None,
      pageNo = None,
      execMs = Instant.now().toEpochMilli - start.toEpochMilli)

  implicit def writes[A](implicit a: Writes[A]): Writes[PublicApiResponse[A]] = Json.writes[PublicApiResponse[A]]
}
