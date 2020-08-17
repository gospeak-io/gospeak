package gospeak.core.services.cloudinary

import cats.effect.IO
import gospeak.core.domain.{ExternalEvent, Group, Partner, User}
import gospeak.libs.scala.domain.{Avatar, Banner, Logo}

trait UploadSrv {
  def signRequest(params: Map[String, String]): Either[String, String]

  def uploadAvatar(user: User): IO[Avatar]

  def uploadGroupLogo(group: Group, logo: Logo): IO[Logo]

  def uploadGroupBanner(group: Group, banner: Banner): IO[Banner]

  def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo]

  def uploadExternalEventLogo(event: ExternalEvent, logo: Logo): IO[Logo]
}

object UploadSrv {
  def userAvatarFile: Option[String] = Some("avatar")

  def groupLogoFile: Option[String] = Some("logo")

  def groupBannerFile: Option[String] = Some("banner")

  def groupPartnerFile(partnerSlug: Option[String]): Option[String] = partnerSlug.filter(_.nonEmpty)

  def groupSlackBotFile: Option[String] = Some("slack-bot-avatar")

  def extEventLogoFile(eventName: Option[String]): Option[String] = eventName.filter(_.nonEmpty)


  def userFolder(user: User): Option[String] = Some(s"users/${user.slug.value}_${user.id.value}")

  def groupFolder(group: Group): Option[String] = Some(s"groups/${group.slug.value}_${group.id.value}")

  def groupPartnerFolder(group: Group): Option[String] = groupFolder(group).map(_ + "/partners")

  def extEventFolder(): Option[String] = Some(s"ext-events")
}
