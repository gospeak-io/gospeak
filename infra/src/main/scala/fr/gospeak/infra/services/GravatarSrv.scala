package fr.gospeak.infra.services

import fr.gospeak.infra.services.GravatarSrv._
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress, Url}
import fr.gospeak.libs.scalautils.utils.Crypto

// see https://fr.gravatar.com/site/implement/images
class GravatarSrv {
  def getAvatar(email: EmailAddress, size: Int = 100, default: String = "wavatar"): Avatar = GravatarSrv.getAvatar(email, size, default)

  def getAvatarUrl(email: EmailAddress, size: Int = 100, default: String = "wavatar"): Url = GravatarSrv.getAvatarUrl(email, size, default)

  def getHash(email: EmailAddress): Hash = GravatarSrv.getHash(email)
}

object GravatarSrv {

  final case class Hash(value: String) extends AnyVal

  def getAvatar(email: EmailAddress, size: Int = 100, default: String = "wavatar"): Avatar = {
    val url = getAvatarUrl(email, size, default)
    Avatar(url, Avatar.Source.Gravatar)
  }

  def getAvatarUrl(email: EmailAddress, size: Int = 100, default: String = "wavatar"): Url = {
    val hash = getHash(email)
    Url.from(s"https://secure.gravatar.com/avatar/${hash.value}?size=$size&default=$default").right.get
  }

  def getHash(email: EmailAddress): Hash = Hash(Crypto.md5(email.value.trim.toLowerCase))

}
