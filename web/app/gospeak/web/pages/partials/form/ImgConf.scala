package gospeak.web.pages.partials.form

import gospeak.core.services.cloudinary.UploadSrv
import gospeak.web.utils.{OrgaReq, UserReq}
import play.api.data.Field
import play.api.mvc.{AnyContent, Call}
import gospeak.web.api.ui.routes.SuggestCtrl

final case class ImgConf(folder: Option[String],
                         name: Option[String],
                         dynamicName: Option[String],
                         tags: Seq[String],
                         maxFiles: Option[Int],
                         ratio: Option[Double],
                         select: Option[Call],
                         args: Seq[(String, String)])

object ImgConf {
  def userAvatar()(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = UploadSrv.userFolder(req.user),
      name = UploadSrv.userAvatarFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      select = None,
      args = Seq())

  def groupLogo(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = UploadSrv.groupFolder(req.group),
      name = UploadSrv.groupLogoFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      select = None,
      args = args)

  def groupBanner(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = UploadSrv.groupFolder(req.group),
      name = UploadSrv.groupBannerFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(3),
      select = None,
      args = args)

  def partnerLogo(partnerSlug: Field)(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = UploadSrv.groupPartnerFolder(req.group),
      name = UploadSrv.groupPartnerFile(partnerSlug.value),
      dynamicName = Some(partnerSlug.id),
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      select = None,
      args = Seq())

  def slackBotAvatar(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = UploadSrv.groupFolder(req.group),
      name = UploadSrv.groupSlackBotFile,
      dynamicName = None,
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      select = None,
      args = Seq())

  def externalEventLogo(eventName: Field)(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = UploadSrv.extEventFolder(),
      name = UploadSrv.extEventLogoFile(eventName.value),
      dynamicName = Some(eventName.id),
      tags = Seq(req.user.slug.value, req.user.id.value),
      maxFiles = Some(1),
      ratio = Some(1),
      select = Some(SuggestCtrl.logosExternalEvents()),
      args = Seq())
}
