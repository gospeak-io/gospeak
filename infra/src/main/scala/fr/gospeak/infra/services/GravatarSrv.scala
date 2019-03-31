package fr.gospeak.infra.services

import java.security.MessageDigest

import fr.gospeak.infra.services.GravatarSrv._
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress, Url}

// see https://fr.gravatar.com/site/implement/images
class GravatarSrv {
  def getAvatar(email: EmailAddress, size: Int = 100, default: String = "wavatar"): Avatar = {
    val url = getAvatarUrl(email, size, default)
    Avatar(url, Avatar.Source.Gravatar)
  }

  def getAvatarUrl(email: EmailAddress, size: Int = 100, default: String = "wavatar"): Url = {
    val hash = getHash(email)
    Url.from(s"https://secure.gravatar.com/avatar/${hash.value}?size=$size&default=$default").right.get
  }

  def getHash(email: EmailAddress): Hash = {
    val str = email.value.trim.toLowerCase
    Hash(MessageDigest.getInstance("MD5").digest(str.getBytes).map("%02x".format(_)).mkString)
  }
}

object GravatarSrv {

  final case class Hash(value: String) extends AnyVal

}
