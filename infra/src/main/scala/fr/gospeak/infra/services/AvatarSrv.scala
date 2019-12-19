package fr.gospeak.infra.services

import java.net.URLEncoder

import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.domain.Image.{AdorableUrl, GravatarUrl}
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress}

class AvatarSrv {
  def getDefault(email: EmailAddress, slug: User.Slug): Avatar = {
    val size = 200
    val default = AdorableUrl(slug.value, Some(size)).value
    val gravatar = GravatarUrl(email, Seq("size" -> s"$size", "default" -> URLEncoder.encode(default, "UTF8")))
    Avatar(gravatar.toUrl)
  }
}
