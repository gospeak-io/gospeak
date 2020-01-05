package fr.gospeak.web.api.domain

import java.time.Instant

import fr.gospeak.core.domain.Comment
import fr.gospeak.core.domain.utils.BasicCtx
import play.api.libs.json.{Json, Writes}

final case class ApiComment(id: String,
                            answers: Option[String],
                            text: String,
                            createdAt: Instant,
                            createdBy: ApiUser.Embed)

object ApiComment {
  implicit val writes: Writes[ApiComment] = Json.writes[ApiComment]

  def from(c: Comment.Full)(implicit ctx: BasicCtx): ApiComment =
    new ApiComment(
      id = c.id.value,
      answers = c.answers.map(_.value),
      text = c.text,
      createdAt = c.createdAt,
      createdBy = ApiUser.embed(c.createdBy))
}
