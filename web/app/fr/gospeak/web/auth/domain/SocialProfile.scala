package fr.gospeak.web.auth.domain

import java.time.Instant

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{Avatar, CustomException, EmailAddress, SocialProvider, Url}

case class SocialProfile(loginInfo: LoginInfo,
                         firstName: Option[String],
                         lastName: Option[String],
                         fullName: Option[String],
                         email: Option[String],
                         avatarURL: Option[String]) {
  def toUserWith(f: EmailAddress => Avatar): Either[CustomException, User] = {
    val now = Instant.now()
    for {
      socialProvider <- SocialProvider.from(loginInfo.providerID)
      source <- socialProvider.toSource
      email <- email.map(EmailAddress.from) match {
        case None => Left(CustomException(
          s"""The email is missing from the social provider response!
          Add the email on your social provider and please try again.
          Otherwise, we cannot go further :(""".stripMargin))
        case Some(p) => p
      }
      slug <- User.Slug.from(StringUtils.slugify(firstName.getOrElse(email.nickName)))
      avatar <- avatarURL.map(Url.from) match {
        case None => Right(f(email))
        case Some(p) => p.map(Avatar(_, source))
      }
    } yield User(
      User.Id.generate(),
      slug,
      // Not sure if missing firstName or lastName should fail the authentication,
      // so i'm using empty string by default for now
      firstName.getOrElse(""),
      lastName.getOrElse(""),
      email,
      Some(now),
      avatar,
      User.emptyProfile,
      now,
      now
    )
  }
}

object SocialProfile {
  def from(socialProfile: CommonSocialProfile): SocialProfile = {
    SocialProfile(socialProfile.loginInfo,
      socialProfile.firstName,
      socialProfile.lastName,
      socialProfile.fullName,
      socialProfile.email,
      socialProfile.avatarURL)
  }

}

