package gospeak.web.pages.partials.form

import gospeak.infra.services.cloudinary.CloudinarySrvImpl
import gospeak.web.utils.{OrgaReq, UserReq}
import play.api.data.Field
import play.api.mvc.AnyContent

final case class ImgConf(folder: Option[String],
                         name: Option[String],
                         dynamicName: Option[String],
                         tags: Seq[String],
                         maxFiles: Option[Int],
                         ratio: Option[Double],
                         args: Seq[(String, String)])

object ImgConf {
  def userAvatar()(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.userFolder(req.user),
      name = CloudinarySrvImpl.userAvatarFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def groupLogo(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupFolder(req.group),
      name = CloudinarySrvImpl.groupLogoFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = args)

  def groupBanner(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupFolder(req.group),
      name = CloudinarySrvImpl.groupBannerFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(3),
      args = args)

  def partnerLogo(partnerSlug: Field)(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupPartnerFolder(req.group),
      name = CloudinarySrvImpl.groupPartnerFile(partnerSlug.value),
      dynamicName = Some(partnerSlug.id),
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def slackBotAvatar(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupFolder(req.group),
      name = CloudinarySrvImpl.groupSlackBotFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def externalEventLogo(eventName: Field)(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.extEventFolder(),
      name = CloudinarySrvImpl.extEventLogoFile(eventName.value),
      dynamicName = Some(eventName.id),
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())
}
