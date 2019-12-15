package fr.gospeak.web.pages.partials.form

import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.web.utils.{OrgaReq, UserReq}
import play.api.mvc.AnyContent

final case class ImgConf(folder: String,
                         name: Option[String],
                         tags: Seq[String],
                         maxFiles: Option[Int],
                         ratio: Option[Double],
                         args: Seq[(String, String)])

object ImgConf {
  def externalCfpLogo()(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = s"ext-cfps",
      name = None,
      tags = Seq(req.user.id.value, req.user.slug.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def userAvatar()(implicit req: UserReq[AnyContent]): ImgConf =
    ImgConf(
      folder = userFolder(req.user),
      name = Some("avatar"),
      tags = Seq(req.user.id.value, req.user.slug.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def groupLogo(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = groupFolder(req.group),
      name = Some("logo"),
      tags = Seq(req.user.id.value, req.user.slug.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = args)

  def groupBanner(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = groupFolder(req.group),
      name = Some("banner"),
      tags = Seq(req.user.id.value, req.user.slug.value),
      maxFiles = Some(1),
      ratio = Some(3),
      args = args)

  def partnerLogo()(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = groupFolder(req.group) + "/partners",
      name = None,
      tags = Seq(req.user.id.value, req.user.slug.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  def slackBotAvatar(args: Seq[(String, String)])(implicit req: OrgaReq[AnyContent]): ImgConf =
    ImgConf(
      folder = groupFolder(req.group),
      name = Some("slack-bot-avatar"),
      tags = Seq(req.user.id.value, req.user.slug.value),
      maxFiles = Some(1),
      ratio = Some(1),
      args = Seq())

  private def userFolder(user: User): String = s"users/${user.slug.value}/${user.id.value}"

  private def groupFolder(group: Group): String = s"groups/${group.slug.value}/${group.id.value}"
}
