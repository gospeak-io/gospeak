package fr.gospeak.web.auth.domain

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
  def toUserData: Either[CustomException, User.Data] = {
    for {
      socialProvider <- SocialProvider.from(loginInfo.providerID)
      source <- socialProvider.toSource
      slug <- User.Slug.from(StringUtils.slugify(firstName.getOrElse("firstName")))
      avatar <- Url.from(avatarURL.getOrElse("https://api.adorable.io/avatars/285/abott@adorable.png"))
      email <- email.map(EmailAddress.from) match {
        case None => Left(CustomException("Email is missing ! Sorry, we cannot go further."))
        case Some(p) => p
      }
    } yield User.Data(
      slug,
      firstName.getOrElse(""),
      lastName.getOrElse(""),
      email,
      Avatar(avatar, source))
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

