package gospeak.infra.services.cloudinary

import cats.effect.IO
import gospeak.core.domain.{ExternalCfp, Group, Partner, User}
import gospeak.core.services.cloudinary.CloudinarySrv
import gospeak.core.services.upload.UploadConf
import gospeak.libs.cloudinary.CloudinaryClient
import gospeak.libs.cloudinary.domain.CloudinaryUploadRequest
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Avatar, Banner, Logo}

class CloudinarySrvImpl(client: CloudinaryClient) extends CloudinarySrv {
  override def signRequest(params: Map[String, String]): Either[String, String] = client.sign(params)

  override def uploadAvatar(user: User): IO[Avatar] = {
    client.upload(CloudinaryUploadRequest(
      file = user.avatar.value,
      folder = CloudinarySrvImpl.userFolder(user),
      publicId = CloudinarySrvImpl.userAvatarFile
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Avatar)
  }

  override def uploadExternalCfpLogo(cfp: ExternalCfp, logo: Logo): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = logo.value,
      folder = CloudinarySrvImpl.extCfpFolder(),
      publicId = CloudinarySrvImpl.extCfpLogoFile(Some(cfp.name.value))
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }

  override def uploadGroupLogo(group: Group, logo: Logo): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = logo.value,
      folder = CloudinarySrvImpl.groupFolder(group),
      publicId = CloudinarySrvImpl.groupLogoFile
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }

  override def uploadGroupBanner(group: Group, banner: Banner): IO[Banner] = {
    client.upload(CloudinaryUploadRequest(
      file = banner.value,
      folder = CloudinarySrvImpl.groupFolder(group),
      publicId = CloudinarySrvImpl.groupBannerFile
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_3", "c_crop")).toUrl).map(Banner)
  }

  override def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = partner.logo.value,
      folder = CloudinarySrvImpl.groupPartnerFolder(group),
      publicId = CloudinarySrvImpl.groupPartnerFile(Some(partner.slug.value))
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }
}

object CloudinarySrvImpl {
  def from(conf: UploadConf.Cloudinary): CloudinarySrvImpl =
    new CloudinarySrvImpl(new CloudinaryClient(CloudinaryClient.Conf(
      cloudName = conf.cloudName,
      uploadPreset = conf.uploadPreset,
      creds = conf.creds)))

  def userAvatarFile: Option[String] = Some("avatar")

  def groupLogoFile: Option[String] = Some("logo")

  def groupBannerFile: Option[String] = Some("banner")

  def groupPartnerFile(partnerSlug: Option[String]): Option[String] = partnerSlug.filter(_.nonEmpty)

  def groupSlackBotFile: Option[String] = Some("slack-bot-avatar")

  def extCfpLogoFile(cfpName: Option[String]): Option[String] = cfpName.filter(_.nonEmpty)


  def userFolder(user: User): Option[String] = Some(s"users/${user.slug.value}_${user.id.value}")

  def groupFolder(group: Group): Option[String] = Some(s"groups/${group.slug.value}_${group.id.value}")

  def groupPartnerFolder(group: Group): Option[String] = groupFolder(group).map(_ + "/partners")

  def extCfpFolder(): Option[String] = Some(s"ext-cfps")
}
