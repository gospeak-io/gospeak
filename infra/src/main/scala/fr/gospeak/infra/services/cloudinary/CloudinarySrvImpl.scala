package fr.gospeak.infra.services.cloudinary

import cats.effect.IO
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.cloudinary.CloudinarySrv
import fr.gospeak.infra.libs.cloudinary.CloudinaryClient
import fr.gospeak.infra.libs.cloudinary.domain.CloudinaryUploadRequest
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Avatar

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
}

object CloudinarySrvImpl {
  val userAvatarId = Some("avatar")
  val groupLogoId = Some("logo")
  val groupBannerId = Some("banner")
  val groupPartnerId = Option.empty[String]
  val groupSlackBotId = Some("slack-bot-avatar")
  val extCfpLogoId = Option.empty[String]

  def userFolder(user: User): Option[String] = Some(s"users/${user.slug.value}/${user.id.value}")

  def groupFolder(group: Group): Option[String] = Some(s"groups/${group.slug.value}/${group.id.value}")

  def groupPartnerFolder(group: Group): Option[String] = groupFolder(group).map(_ + "/partners")

  def extCfpFolder(): Option[String] = Some(s"ext-cfps")
}
