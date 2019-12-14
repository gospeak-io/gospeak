package fr.gospeak.web.pages.partials.form

import fr.gospeak.web.utils.{OrgaReq, UserReq}
import play.api.mvc.AnyContent

final case class ImgConf(folder: String,
                         name: Option[String],
                         maxFiles: Option[Int],
                         ratio: Option[Double],
                         args: Seq[(String, String)])

object ImgConf {
  def userAvatar()(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = s"users/${req.user.id.value}",
      name = Some("avatar"),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def externalCfpLogo(): ImgConf =
    ImgConf(
      folder = s"ext-cfps",
      name = None,
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def groupLogo(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = s"groups/${req.group.id.value}",
      name = Some("logo"),
      maxFiles = Some(1),
      ratio = Some(1),
      args = args)

  def groupBanner(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = s"groups/${req.group.id.value}",
      name = Some("banner"),
      maxFiles = Some(1),
      ratio = Some(3),
      args = args)

  def partnerLogo()(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = s"groups/${req.group.id.value}/partners",
      name = None,
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def slackBotAvatar(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = s"groups/${req.group.id.value}",
      name = Some("slack-bot-avatar"),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())
}
