package fr.gospeak.migration.utils

import java.io.FileNotFoundException
import java.net.URL

import fr.gospeak.libs.scalautils.Crypto
import fr.gospeak.libs.scalautils.domain.{Avatar, EmailAddress, Url}

import scala.io.Source
import scala.util.Try
import scala.util.control.NonFatal

object AvatarUtils {
  def buildAvatarQuick(avatar: Option[String], email: EmailAddress): Avatar = {
    avatar
      .flatMap(Url.from(_).toOption)
      .map(url => Avatar(url, getKind(url.value)))
      .getOrElse(getGravatar(email))
  }

  def buildAvatar(avatar: Option[String], email: EmailAddress): Avatar = {
    buildAvatarQuick(avatar.filter(getStatusCode(_) == 200), email)
  }

  def getGravatar(email: EmailAddress): Avatar = {
    val hash = Crypto.md5(email.value.trim.toLowerCase)
    val url = Url.from(s"https://secure.gravatar.com/avatar/$hash?size=100&default=wavatar").right.get
    Avatar(url, Avatar.Source.Gravatar)
  }

  def getKind(url: String): Avatar.Source = {
    if (url.startsWith("https://pbs.twimg.com/profile_images")) Avatar.Source.Twitter
    else if (url.contains("gravatar.com/avatar/")) Avatar.Source.Gravatar
    else if (url.startsWith("https://media.licdn.com")) Avatar.Source.LinkedIn
    else Avatar.Source.UserDefined
  }

  def getStatusCode(url: String): Int = {
    val source = Try(Source.fromURL(new URL(url)))
    try {
      source.get.getLines()
      200
    } catch {
      case _: FileNotFoundException => 404
      case NonFatal(e) =>
        println(s"getStatusCode($url) => ${e.getClass.getSimpleName}: ${e.getMessage}")
        500
    } finally {
      source.map(_.close())
    }
  }
}
