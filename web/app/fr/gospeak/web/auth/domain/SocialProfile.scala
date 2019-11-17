package fr.gospeak.web.auth.domain

import java.time.Instant

import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import fr.gospeak.core.domain.User
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain._

object SocialProfile {
  val setEmailUrls: Map[String, String] = Map(
    "twitter" -> "https://twitter.com/settings/email")

  def toUser(profile: CommonSocialProfile, defaultAvatar: EmailAddress => Avatar, now: Instant): Either[CustomException, User] =
    for {
      email <- profile.email.map(EmailAddress.from)
        .getOrElse(Left(CustomException(s"<b>No email available from your ${profile.loginInfo.providerID} account.</b><br>" +
          s"${setEmailUrls.get(profile.loginInfo.providerID).map(url => "<a href=\"" + url + "\" target=\"_blank\">Add your email</a>").getOrElse("Add your email")} " +
          s"and try again or choose an other login option.")))
      avatarOpt <- getAvatar(profile)
      slug <- User.Slug.from(StringUtils.slugify(profile.firstName.getOrElse(email.nickName)))
      (first, last) = email.guessNames
    } yield User(
      id = User.Id.generate(),
      slug = slug,
      firstName = profile.firstName.getOrElse(first),
      lastName = profile.lastName.getOrElse(last),
      email = email,
      emailValidated = Some(now),
      avatar = avatarOpt.getOrElse(defaultAvatar(email)),
      profile = User.emptyProfile,
      created = now,
      updated = now)

  def getAvatar(profile: CommonSocialProfile): Either[CustomException, Option[Avatar]] = for {
    source <- Avatar.Source.all.find(_.toString.toLowerCase == profile.loginInfo.providerID)
      .toEither(CustomException(s"No Avatar.Source for providerID: ${profile.loginInfo.providerID}"))
    avatar <- profile.avatarURL.map(Url.from(_).map(Avatar(_, source))).sequence
  } yield avatar
}
