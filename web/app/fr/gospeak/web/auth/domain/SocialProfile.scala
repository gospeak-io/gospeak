package fr.gospeak.web.auth.domain

import java.time.Instant

import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.SocialAccounts
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.StringUtils
import gospeak.libs.scala.domain.{Avatar, CustomException, EmailAddress, Url}

object SocialProfile {
  val setEmailUrls: Map[String, String] = Map(
    "twitter" -> "https://twitter.com/settings/email")

  def toUserData(profile: CommonSocialProfile, defaultAvatar: (EmailAddress, User.Slug) => Avatar, now: Instant): Either[CustomException, User.Data] =
    for {
      email <- profile.email.map(EmailAddress.from)
        .getOrElse(Left(CustomException(s"<b>No email available from your ${profile.loginInfo.providerID} account.</b><br>" +
          s"${setEmailUrls.get(profile.loginInfo.providerID).map(url => "<a href=\"" + url + "\" target=\"_blank\">Add your email</a>").getOrElse("Add your email")} " +
          s"and try again or choose an other login option.")))
      avatarOpt <- getAvatar(profile)
      slug <- User.Slug.from(StringUtils.slugify(profile.firstName.getOrElse(email.nickName)))
      (first, last) = email.guessNames
    } yield User.Data(
      slug = slug,
      status = User.Status.Undefined,
      firstName = profile.firstName.getOrElse(first),
      lastName = profile.lastName.getOrElse(last),
      email = email,
      avatar = avatarOpt.getOrElse(defaultAvatar(email, slug)),
      bio = None,
      company = None,
      location = None,
      phone = None,
      website = None,
      social = SocialAccounts.fromUrls())

  def getAvatar(profile: CommonSocialProfile): Either[CustomException, Option[Avatar]] =
    profile.avatarURL.map(Url.from).sequence.map(_.map(Avatar))
}
