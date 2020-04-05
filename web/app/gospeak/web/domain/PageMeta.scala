package gospeak.web.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Constants
import gospeak.core.domain._
import gospeak.libs.scala.domain.{GMapPlace, Geo, Logo, Markdown, Url}
import gospeak.web.pages.published.routes.HomeCtrl
import gospeak.web.pages.published.cfps.routes.CfpCtrl
import gospeak.web.pages.published.events.routes.EventCtrl
import gospeak.web.pages.published.groups.routes.GroupCtrl
import gospeak.web.pages.published.speakers.routes.SpeakerCtrl
import gospeak.web.utils.{BasicReq, Formats}
import play.api.mvc.{AnyContent, Call}

// https://search.google.com/structured-data/testing-tool
// https://cards-dev.twitter.com/validator
// https://developers.facebook.com/tools/debug
final case class PageMeta(kind: String,
                          title: String, // max 70 chars
                          description: String, // max 200 chars
                          icon: String, // 1x1 image
                          url: String,
                          breadcrumb: Breadcrumb,
                          organization: PageMeta.SEOOrganization,
                          event: Option[PageMeta.SEOEvent] = None,
                          start: Option[PageMeta.SEODate] = None,
                          end: Option[PageMeta.SEODate] = None,
                          location: Option[PageMeta.SEOLocation] = None)

object PageMeta {

  final case class SEOOrganization(name: String, url: Url, logo: Logo)

  object SEOOrganization {
    def apply(g: Group): SEOOrganization = new SEOOrganization(
      name = g.name.value,
      url = g.website.getOrElse(Constants.Gospeak.url),
      logo = g.logo.getOrElse(Constants.Gospeak.logo))
  }

  // cf https://developers.google.com/search/docs/data-types/event
  final case class SEOEvent(name: String,
                            icon: String)

  object SEOEvent {
    def apply(e: Event.Full): SEOEvent = new SEOEvent(
      name = e.name.value,
      icon = e.group.logo.getOrElse(Constants.Gospeak.logo).value)

    def apply(e: ExternalEvent): SEOEvent = new SEOEvent(
      name = e.name.value,
      icon = e.logo.getOrElse(Constants.Gospeak.logo).value)
  }

  final case class SEODate(private val value: LocalDateTime) {
    def human: String = if (value.getHour == 0) Formats.date(value) else Formats.datetime(value)

    def iso: String = if (value.getHour == 0) value.toLocalDate.toString else value.toString
  }

  final case class SEOLocation(name: String,
                               street: Option[String],
                               locality: Option[String],
                               postalCode: Option[String],
                               country: String,
                               coords: Geo)

  object SEOLocation {
    def apply(p: GMapPlace): SEOLocation = new SEOLocation(
      name = p.name,
      street = p.street.map(p.streetNo.map(_ + " ").getOrElse("") + _),
      locality = p.locality,
      postalCode = p.postalCode,
      country = p.country,
      coords = p.geo)
  }

  private val gospeakOrganization = SEOOrganization(Constants.Gospeak.name, Constants.Gospeak.url, Constants.Gospeak.logo)

  def default(call: Call)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "website",
    title = s"${Constants.Emoji.rocket} Helping people to become speaker with a welcoming community",
    description = "Gospeak help people speak publicly. Find advices, mentoring and places to speak. Then publish your experiences and improve your personal branding.",
    icon = Constants.Gospeak.logo.value,
    url = req.toAbsolute(call),
    breadcrumb = Breadcrumb("Home", HomeCtrl.index()),
    organization = gospeakOrganization)

  def user(u: User, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.user} ${(u.name.value + u.title.map(t => " - " + t).getOrElse("")).take(67)}",
    description = u.bio.map(_.toText.take(200)).getOrElse(""),
    icon = u.avatar.value,
    url = req.toAbsolute(SpeakerCtrl.detail(u.slug)),
    breadcrumb = b,
    organization = gospeakOrganization)

  def talk(u: User, t: Talk, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.talk} ${t.title.value.take(67)}",
    description = t.description.toText.take(200),
    icon = u.avatar.value,
    url = req.toAbsolute(SpeakerCtrl.talk(u.slug, t.slug)),
    breadcrumb = b,
    organization = gospeakOrganization)

  def group(g: Group, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.group} ${g.name.value.take(67)}",
    description = g.description.toText.take(200),
    icon = g.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(GroupCtrl.detail(g.slug)),
    breadcrumb = b,
    organization = SEOOrganization(g))

  def cfp(g: Group, c: Cfp, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.cfp} ${c.name.value.take(67)}",
    description = c.description.toText.take(200),
    icon = g.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(CfpCtrl.detail(c.slug)),
    breadcrumb = b,
    organization = SEOOrganization(g),
    start = c.begin.map(SEODate),
    end = c.close.map(SEODate))

  def cfp(c: ExternalCfp.Full, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.cfp} ${c.event.name.value.take(67)}",
    description = c.description.toText.take(200),
    icon = c.event.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(CfpCtrl.detailExt(c.id)),
    breadcrumb = b,
    organization = gospeakOrganization,
    start = c.begin.map(SEODate),
    end = c.close.map(SEODate))

  def event(e: Event.Full, description: Markdown, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "events.event",
    title = s"${Constants.Emoji.event} ${e.name.value.take(67)}",
    description = s"${SEODate(e.start).human}: ${description.toText}".take(200),
    icon = e.group.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(GroupCtrl.event(e.group.slug, e.slug)),
    breadcrumb = b,
    organization = SEOOrganization(e.group),
    event = Some(SEOEvent(e)),
    location = e.venue.map(v => SEOLocation(v.address)),
    start = Some(SEODate(e.start)))

  def event(e: ExternalEvent, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "events.event",
    title = s"${Constants.Emoji.event} ${e.name.value.take(67)}",
    description = s"${e.start.map(SEODate(_).human + ": ").getOrElse("")}${e.description.toText}".take(200),
    icon = e.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(EventCtrl.detailExt(e.id)),
    breadcrumb = b,
    organization = gospeakOrganization,
    event = Some(SEOEvent(e)),
    location = e.location.map(SEOLocation(_)),
    start = e.start.map(SEODate),
    end = e.finish.map(SEODate))

  def proposal(g: Group, p: Proposal, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.proposal} ${p.title.value.take(67)}",
    description = p.description.toText.take(200),
    icon = g.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(GroupCtrl.talk(g.slug, p.id)),
    breadcrumb = b,
    organization = SEOOrganization(g))

  def proposal(e: ExternalEvent, p: ExternalProposal, b: Breadcrumb)(implicit req: BasicReq[AnyContent]): PageMeta = PageMeta(
    kind = "article",
    title = s"${Constants.Emoji.proposal} ${p.title.value.take(67)}",
    description = p.description.toText.take(200),
    icon = e.logo.getOrElse(Constants.Gospeak.logo).value,
    url = req.toAbsolute(EventCtrl.proposalExt(e.id, p.id)),
    breadcrumb = b,
    organization = gospeakOrganization)
}
