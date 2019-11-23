package fr.gospeak.migration.utils

import java.io.FileNotFoundException
import java.net.URL

import fr.gospeak.libs.scalautils.Crypto
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress, Url}

import scala.io.Source
import scala.util.Try
import scala.util.control.NonFatal

object AvatarUtils {
  def buildAvatarQuick(avatar: Option[String], email: EmailAddress): Avatar =
    avatar.flatMap(Url.from(_).toOption).map(Avatar).getOrElse(getGravatar(email))

  def buildAvatar(avatar: Option[String], email: EmailAddress): Avatar = {
    buildAvatarQuick(avatar.filter(getStatusCode(_) == 200), email)
  }

  def getGravatar(email: EmailAddress): Avatar = {
    val hash = Crypto.md5(email.value.trim.toLowerCase)
    val url = Url.from(s"https://secure.gravatar.com/avatar/$hash?size=100&default=wavatar").right.get
    Avatar(url)
  }

  def getStatusCode(url: String): Int = {
    val source = Try(Source.fromURL(new URL(url)))
    try {
      source.get.getLines()
      200
    } catch {
      case _: FileNotFoundException => 404
      case NonFatal(_) => 500
    } finally {
      source.map(_.close())
    }
  }
}
