package fr.gospeak.infra.services.cloudinary

import cats.effect.IO
import fr.gospeak.core.domain.{Group, Partner, User}
import fr.gospeak.core.services.cloudinary.CloudinarySrv
import fr.gospeak.infra.libs.cloudinary.CloudinaryClient
import fr.gospeak.infra.libs.cloudinary.domain.CloudinaryUploadRequest
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Avatar, Banner, Logo}

class CloudinarySrvImpl(client: CloudinaryClient) extends CloudinarySrv {
  override def signRequest(params: Map[String, String]): Either[String, String] = client.sign(params)

  override def uploadAvatar(user: User): IO[Avatar] = {
    client.upload(CloudinaryUploadRequest(
      file = user.avatar.value,
      folder = CloudinarySrvImpl.userFolder(user),
      publicId = CloudinarySrvImpl.userAvatarId
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Avatar)
  }

  override def uploadExternalCfpLogo(logo: Logo): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = logo.value,
      folder = CloudinarySrvImpl.extCfpFolder(),
      publicId = CloudinarySrvImpl.extCfpLogoId
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }

  override def uploadGroupLogo(group: Group, logo: Logo): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = logo.value,
      folder = CloudinarySrvImpl.groupFolder(group),
      publicId = CloudinarySrvImpl.groupLogoId
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }

  override def uploadGroupBanner(group: Group, banner: Banner): IO[Banner] = {
    client.upload(CloudinaryUploadRequest(
      file = banner.value,
      folder = CloudinarySrvImpl.groupFolder(group),
      publicId = CloudinarySrvImpl.groupBannerId
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_3", "c_crop")).toUrl).map(Banner)
  }

  override def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = partner.logo.value,
      folder = CloudinarySrvImpl.groupPartnerFolder(group),
      publicId = CloudinarySrvImpl.groupPartnerId
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }
}

object CloudinarySrvImpl {
  val userAvatarId = Some("avatar")
  val groupLogoId = Some("logo")
  val groupBannerId = Some("banner")
  val groupPartnerId = Option.empty[String]
  val groupSlackBotId = Some("slack-bot-avatar")
  val extCfpLogoId = Option.empty[String]

  def userFolder(user: User): Option[String] = Some(s"users/${user.slug.value}_${user.id.value}")

  def groupFolder(group: Group): Option[String] = Some(s"groups/${group.slug.value}_${group.id.value}")

  def groupPartnerFolder(group: Group): Option[String] = groupFolder(group).map(_ + "/partners")

  def extCfpFolder(): Option[String] = Some(s"ext-cfps")
}
