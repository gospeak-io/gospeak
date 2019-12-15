package fr.gospeak.core.services.cloudinary

import cats.effect.IO
import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.Avatar

trait CloudinarySrv {
  def signRequest(params: Map[String, String]): Either[String, String]

  def uploadAvatar(user: User): IO[Avatar]
}
