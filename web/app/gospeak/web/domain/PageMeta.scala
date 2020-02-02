package gospeak.web.domain

import gospeak.core.domain.utils.Constants
import gospeak.core.domain.{Cfp, Event, ExternalCfp, Group, Proposal, User}
import gospeak.web.pages.published.cfps.routes.CfpCtrl
import gospeak.web.pages.published.groups.routes.GroupCtrl
import gospeak.web.pages.published.routes.HomeCtrl
import gospeak.web.pages.published.speakers.routes.SpeakerCtrl
import gospeak.web.utils.BasicReq
import play.api.mvc.AnyContent

// https://cards-dev.twitter.com/validator
// https://developers.facebook.com/tools/debug
final case class PageMeta(title: String, // max 70 chars
                          description: String, // max 200 chars
                          image: String,
                          url: String,
                          twitterCard: String) // values: summary (square image), summary_large_image (banner image)

object PageMeta {
  def default()(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = "The best platform to find a place to speak",
    description = "Gospeak help people speak publicly. Find advices, mentoring and places to speak. Then publish your experiences and improve your personal branding.",
    image = Constants.Images.gospeakLogoSquare,
    url = req.format(HomeCtrl.index()),
    twitterCard = "summary")

  def user(u: User)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = u.name.value.take(70),
    description = u.bio.map(_.value.take(200)).getOrElse(""),
    image = u.avatar.value,
    url = req.format(SpeakerCtrl.detail(u.slug)),
    twitterCard = "summary")

  def group(g: Group)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = g.name.value.take(70),
    description = g.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.detail(g.slug)),
    twitterCard = "summary")

  def event(g: Group, e: Event)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = e.name.value.take(70),
    description = e.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.event(g.slug, e.slug)),
    twitterCard = "summary")

  def talk(g: Group, p: Proposal)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = p.title.value.take(70),
    description = p.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.talk(g.slug, p.id)),
    twitterCard = "summary")

  def cfps()(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = "Gospeak - All the CFPs in one place",
    description = "Gospeak help people speak publicly. Find advices, mentoring and places to speak. Then publish your experiences and improve your personal branding.",
    image = Constants.Images.gospeakLogoSquare,
    url = req.format(CfpCtrl.list()),
    twitterCard = "summary")

  def createCfp()(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = "The best platform to find a place to speak",
    description = "Gospeak help people speak publicly. Find advices, mentoring and places to speak. Then publish your experiences and improve your personal branding.",
    image = Constants.Images.gospeakLogoSquare,
    url = req.format(CfpCtrl.findExternalEvent()),
    twitterCard = "summary")

  def cfp(g: Group, c: Cfp)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = c.name.value.take(70),
    description = c.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(CfpCtrl.detail(c.slug)),
    twitterCard = "summary")

  def cfp(c: ExternalCfp.Full)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = c.event.name.value.take(70),
    description = c.description.value.take(200),
    image = c.event.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(CfpCtrl.detailExt(c.id)),
    twitterCard = "summary")
}
