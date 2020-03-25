package gospeak.web.domain

import gospeak.core.domain.utils.Constants
import gospeak.core.domain._
import gospeak.web.pages.published.cfps.routes.CfpCtrl
import gospeak.web.pages.published.events.routes.EventCtrl
import gospeak.web.pages.published.groups.routes.GroupCtrl
import gospeak.web.pages.published.speakers.routes.SpeakerCtrl
import gospeak.web.utils.BasicReq
import play.api.mvc.{AnyContent, Call}

// https://cards-dev.twitter.com/validator
// https://developers.facebook.com/tools/debug
final case class PageMeta(title: String, // max 70 chars
                          description: String, // max 200 chars
                          image: String,
                          url: String,
                          twitterCard: String) // values: summary (square image), summary_large_image (banner image)

object PageMeta {
  def default(call: Call)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.rocket} Helping people to become speaker with a welcoming community",
    description = "Gospeak help people speak publicly. Find advices, mentoring and places to speak. Then publish your experiences and improve your personal branding.",
    image = Constants.Images.gospeakLogoSquare,
    url = req.format(call),
    twitterCard = "summary")

  def cfp(g: Group, c: Cfp)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.cfp} ${c.name.value.take(67)}",
    description = c.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(CfpCtrl.detail(c.slug)),
    twitterCard = "summary")

  def cfp(c: ExternalCfp.Full)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.cfp} ${c.event.name.value.take(67)}",
    description = c.description.value.take(200),
    image = c.event.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(CfpCtrl.detailExt(c.id)),
    twitterCard = "summary")

  def event(g: Group, e: Event)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.event} ${e.name.value.take(67)}",
    description = e.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.event(g.slug, e.slug)),
    twitterCard = "summary")

  def event(e: ExternalEvent)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.event} ${e.name.value.take(67)}",
    description = e.description.value.take(200),
    image = e.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(EventCtrl.detailExt(e.id)),
    twitterCard = "summary")

  def proposal(e: ExternalEvent, p: ExternalProposal)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.proposal} ${p.title.value.take(67)}",
    description = p.description.value.take(200),
    image = e.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(EventCtrl.proposalExt(e.id, p.id)),
    twitterCard = "summary")

  def user(u: User)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.user} ${(u.name.value + u.title.map(t => " - " + t).getOrElse("")).take(67)}",
    description = u.bio.map(_.value.take(200)).getOrElse(""),
    image = u.avatar.value,
    url = req.format(SpeakerCtrl.detail(u.slug)),
    twitterCard = "summary")

  def talk(u: User, t: Talk)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.talk} ${t.title.value.take(67)}",
    description = t.description.value.take(200),
    image = u.avatar.value,
    url = req.format(SpeakerCtrl.talk(u.slug, t.slug)),
    twitterCard = "summary")

  def group(g: Group)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.group} ${g.name.value.take(67)}",
    description = g.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.detail(g.slug)),
    twitterCard = "summary")

  def proposal(g: Group, p: Proposal)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    title = s"${Constants.Emoji.proposal} ${p.title.value.take(67)}",
    description = p.description.value.take(200),
    image = g.logo.map(_.value).getOrElse(Constants.Images.gospeakLogoSquare),
    url = req.format(GroupCtrl.talk(g.slug, p.id)),
    twitterCard = "summary")
}
