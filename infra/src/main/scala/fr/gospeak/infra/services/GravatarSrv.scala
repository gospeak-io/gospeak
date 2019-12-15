package fr.gospeak.infra.services

import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress, Image, Url}

// see https://fr.gravatar.com/site/implement/images
class GravatarSrv {
  def getAvatar(email: EmailAddress, size: Int = 100, default: String = Constants.gravatarStyle): Avatar =
    Avatar(Url.from(Image.GravatarUrl(email, Seq("size" -> size.toString, "default" -> default)).value).get)
}
