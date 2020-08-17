package gospeak.infra.services.upload

import cats.effect.IO
import gospeak.core.domain.{ExternalEvent, Group, Partner, User}
import gospeak.core.services.cloudinary.UploadSrv
import gospeak.core.services.upload.UploadConf
import gospeak.libs.cloudinary.CloudinaryClient
import gospeak.libs.cloudinary.domain.CloudinaryUploadRequest
import gospeak.libs.http.HttpClient
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Avatar, Banner, Logo}

class CloudinaryUploadSrv(client: CloudinaryClient) extends UploadSrv {
  override def signRequest(params: Map[String, String]): Either[String, String] = client.sign(params)

  override def uploadAvatar(user: User): IO[Avatar] = {
    client.upload(CloudinaryUploadRequest(
      file = user.avatar.value,
      folder = UploadSrv.userFolder(user),
      publicId = UploadSrv.userAvatarFile
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Avatar)
  }

  override def uploadGroupLogo(group: Group, logo: Logo): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = logo.value,
      folder = UploadSrv.groupFolder(group),
      publicId = UploadSrv.groupLogoFile
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }

  override def uploadGroupBanner(group: Group, banner: Banner): IO[Banner] = {
    client.upload(CloudinaryUploadRequest(
      file = banner.value,
      folder = UploadSrv.groupFolder(group),
      publicId = UploadSrv.groupBannerFile
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_3", "c_crop")).toUrl).map(Banner)
  }

  override def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = partner.logo.value,
      folder = UploadSrv.groupPartnerFolder(group),
      publicId = UploadSrv.groupPartnerFile(Some(partner.slug.value))
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }

  override def uploadExternalEventLogo(event: ExternalEvent, logo: Logo): IO[Logo] = {
    client.upload(CloudinaryUploadRequest(
      file = logo.value,
      folder = UploadSrv.extEventFolder(),
      publicId = UploadSrv.extEventLogoFile(Some(event.name.value))
    )).flatMap(_.toIO(new IllegalStateException(_)))
      .flatMap(_.toUrl.toIO)
      .map(_.transform(Seq("ar_1", "c_crop")).toUrl).map(Logo)
  }
}

object CloudinaryUploadSrv {
  def from(conf: UploadConf.Cloudinary, http: HttpClient): CloudinaryUploadSrv =
    new CloudinaryUploadSrv(new CloudinaryClient(CloudinaryClient.Conf(
      cloudName = conf.cloudName,
      uploadPreset = conf.uploadPreset,
      creds = conf.creds),
      http = http))
}
