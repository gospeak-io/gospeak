package gospeak.web.domain

import gospeak.core.domain.utils.Constants
import gospeak.core.domain._
import gospeak.web.pages.published.routes.HomeCtrl
import gospeak.web.pages.published.cfps.routes.CfpCtrl
import gospeak.web.pages.published.events.routes.EventCtrl
import gospeak.web.pages.published.groups.routes.GroupCtrl
import gospeak.web.pages.published.speakers.routes.SpeakerCtrl
import gospeak.web.utils.BasicReq
import play.api.mvc.{AnyContent, Call}

// https://search.google.com/structured-data/testing-tool
// https://cards-dev.twitter.com/validator
// https://developers.facebook.com/tools/debug
final case class PageMeta(title: String, // max 70 chars
                          description: String, // max 200 chars
                          icon: String, // 1x1 image
                          url: String,
                          breadcrumb: Breadcrumb)

object PageMeta {
  def default(call: Call)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.rocket} Helping people to become speaker with a welcoming community",
    description = "Gospeak help people speak publicly. Find advices, mentoring and places to speak. Then publish your experiences and improve your personal branding.",
    icon = Constants.Images.gospeakLogoSquare,
    url = req.format(call),
    breadcrumb = Breadcrumb("Home", HomeCtrl.index()))

  def cfp(g: Group, c: Cfp, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.cfp} ${c.name.value.take(67)}",
    description = c.description.value.take(200),
    icon = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(CfpCtrl.detail(c.slug)),
    breadcrumb = b)

  def cfp(c: ExternalCfp.Full, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.cfp} ${c.event.name.value.take(67)}",
    description = c.description.value.take(200),
    icon = c.event.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(CfpCtrl.detailExt(c.id)),
    breadcrumb = b)

  def event(g: Group, e: Event, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.event} ${e.name.value.take(67)}",
    description = e.description.value.take(200),
    icon = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.event(g.slug, e.slug)),
    breadcrumb = b)

  def event(e: ExternalEvent, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.event} ${e.name.value.take(67)}",
    description = e.description.value.take(200),
    icon = e.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(EventCtrl.detailExt(e.id)),
    breadcrumb = b)

  def proposal(e: ExternalEvent, p: ExternalProposal, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.proposal} ${p.title.value.take(67)}",
    description = p.description.value.take(200),
    icon = e.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(EventCtrl.proposalExt(e.id, p.id)),
    breadcrumb = b)

  def user(u: User, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.user} ${(u.name.value + u.title.map(t => " - " + t).getOrElse("")).take(67)}",
    description = u.bio.map(_.value.take(200)).getOrElse(""),
    icon = u.avatar.value,
    url = req.format(SpeakerCtrl.detail(u.slug)),
    breadcrumb = b)

  def talk(u: User, t: Talk, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.talk} ${t.title.value.take(67)}",
    description = t.description.value.take(200),
    icon = u.avatar.value,
    url = req.format(SpeakerCtrl.talk(u.slug, t.slug)),
    breadcrumb = b)

  def group(g: Group, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.group} ${g.name.value.take(67)}",
    description = g.description.value.take(200),
    icon = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.detail(g.slug)),
    breadcrumb = b)

  def proposal(g: Group, p: Proposal, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.proposal} ${p.title.value.take(67)}",
    description = p.description.value.take(200),
    icon = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.talk(g.slug, p.id)),
    breadcrumb = b)
}
