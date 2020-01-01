package fr.gospeak.web.api.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.{BasicCtx, Info}
import fr.gospeak.web.api.domain.ApiUser
import play.api.libs.json.{Json, Writes}

final case class ApiInfo(createdAt: Instant,
                         createdBy: ApiUser.Embed,
                         updatedAt: Instant,
                         updatedBy: ApiUser.Embed)

object ApiInfo {
  def from(i: Info, users: Seq[User])(implicit ctx: BasicCtx): ApiInfo =
    new ApiInfo(
      createdAt = i.createdAt,
      createdBy = ApiUser.embed(i.createdBy, users),
      updatedAt = i.updatedAt,
      updatedBy = ApiUser.embed(i.updatedBy, users))

  implicit val writes: Writes[ApiInfo] = Json.writes[ApiInfo]
}
