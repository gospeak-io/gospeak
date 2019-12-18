package fr.gospeak.web.pages.partials.form

import fr.gospeak.infra.services.cloudinary.CloudinarySrvImpl
import fr.gospeak.web.utils.{OrgaReq, UserReq}
import play.api.mvc.AnyContent

final case class ImgConf(folder: Option[String],
                         name: Option[String],
                         tags: Seq[String],
                         maxFiles: Option[Int],
                         ratio: Option[Double],
                         args: Seq[(String, String)])

object ImgConf {
  def externalCfpLogo(cfpName: Option[String])(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.extCfpFolder(),
      name = CloudinarySrvImpl.extCfpLogoFile(cfpName),
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def userAvatar()(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.userFolder(req.user),
      name = CloudinarySrvImpl.userAvatarFile,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def groupLogo(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupFolder(req.group),
      name = CloudinarySrvImpl.groupLogoFile,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = args)

  def groupBanner(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupFolder(req.group),
      name = CloudinarySrvImpl.groupBannerFile,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(3),
      args = args)

  def partnerLogo(partnerSlug: Option[String])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupPartnerFolder(req.group),
      name = CloudinarySrvImpl.groupPartnerFile(partnerSlug),
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def slackBotAvatar(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = CloudinarySrvImpl.groupFolder(req.group),
      name = CloudinarySrvImpl.groupSlackBotFile,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())
}
