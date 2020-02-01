package gospeak.web.domain

import gospeak.core.domain._
import gospeak.core.domain.utils.GsMessage.Linked
import gospeak.core.domain.utils.{GsMessage, TemplateData}
import gospeak.web.services.EventSrv.EventFull
import gospeak.web.utils.{BasicReq, OrgaReq, UserReq}
import play.api.mvc.AnyContent

class MessageBuilder {
  def buildEventCreated(event: Event)(implicit req: OrgaReq[AnyContent]): GsMessage.EventCreated =
    GsMessage.EventCreated(linked(req.group), linked(req.group, event), req.user)

  def buildTalkAdded(event: Event, cfp: Cfp, proposal: Proposal)(implicit req: OrgaReq[AnyContent]): GsMessage.TalkAdded =
    GsMessage.TalkAdded(linked(req.group), linked(req.group, event), linked(req.group, cfp), linked(req.group, event, cfp, proposal), req.user)

  def buildTalkRemoved(event: Event, cfp: Cfp, proposal: Proposal)(implicit req: OrgaReq[AnyContent]): GsMessage.TalkRemoved =
    GsMessage.TalkRemoved(linked(req.group), linked(req.group, event), linked(req.group, cfp), linked(req.group, event, cfp, proposal), req.user)

  def buildEventPublished(event: Event)(implicit req: OrgaReq[AnyContent]): GsMessage.EventPublished =
    GsMessage.EventPublished(linked(req.group), linked(req.group, event), req.user)

  def buildProposalCreated(group: Group, cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): GsMessage.ProposalCreated =
    GsMessage.ProposalCreated(linked(group), linked(group, cfp), linked(group, cfp, proposal), req.user)

  def buildExternalCfpCreated(cfp: ExternalCfp)(implicit req: UserReq[AnyContent]): GsMessage.ExternalCfpCreated =
    GsMessage.ExternalCfpCreated(linked(cfp), req.user)

  def buildEventInfo(group: Group, event: Event, cfpOpt: Option[Cfp], venueOpt: Option[Venue.Full], talks: Seq[Proposal], speakers: Seq[User])(implicit req: BasicReq[AnyContent]): TemplateData.EventInfo = {
    TemplateData.eventInfo(
      g = linked(group),
      e = linked(group, event),
      v = venueOpt,
      c = cfpOpt.map(linked(group, _)),
      ts = cfpOpt.map(cfp => talks.map(linked(group, event, cfp, _))).getOrElse(Seq()),
      ss = speakers.map(linked(group, _)))
  }

  def buildEventInfo(i: Event.Full, cfpOpt: Option[Cfp], talks: Seq[Proposal], speakers: Seq[User])(implicit req: BasicReq[AnyContent]): TemplateData.EventInfo =
    buildEventInfo(i.group, i.event, cfpOpt, i.venue, talks, speakers)

  def buildEventInfo(i: EventFull)(implicit req: BasicReq[AnyContent]): TemplateData.EventInfo =
    buildEventInfo(i.group, i.event, i.cfpOpt, i.venueOpt, i.talks, i.speakers)

  private def linked(group: Group)(implicit req: BasicReq[AnyContent]): Linked[Group] = {
    val link = req.format(gospeak.web.pages.orga.routes.GroupCtrl.detail(group.slug))
    val publicLink = Some(req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.detail(group.slug)))
    Linked[Group](group, link, publicLink)
  }

  private def linked(group: Group, event: Event)(implicit req: BasicReq[AnyContent]): Linked[Event] = {
    val link = req.format(gospeak.web.pages.orga.events.routes.EventCtrl.detail(group.slug, event.slug))
    val publicLink = if (event.isPublic) {
      Some(req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.event(group.slug, event.slug)))
    } else {
      None
    }
    Linked[Event](event, link, publicLink)
  }

  private def linked(group: Group, cfp: Cfp)(implicit req: BasicReq[AnyContent]): Linked[Cfp] = {
    val link = req.format(gospeak.web.pages.orga.cfps.routes.CfpCtrl.detail(group.slug, cfp.slug))
    val publicLink = if (cfp.isActive(req.nowLDT)) {
      Some(req.format(gospeak.web.pages.published.cfps.routes.CfpCtrl.detail(cfp.slug)))
    } else {
      None
    }
    Linked[Cfp](cfp, link, publicLink)
  }

  private def linked(group: Group, cfp: Cfp, proposal: Proposal)(implicit req: UserReq[AnyContent]): Linked[Proposal] = {
    val link = req.format(gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id))
    Linked[Proposal](proposal, link, None)
  }

  private def linked(group: Group, event: Event, cfp: Cfp, proposal: Proposal)(implicit req: BasicReq[AnyContent]): Linked[Proposal] = {
    val link = req.format(gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id))
    val publicLink = if (event.isPublic) {
      Some(req.format(gospeak.web.pages.published.groups.routes.GroupCtrl.talk(group.slug, proposal.id)))
    } else {
      None
    }
    Linked[Proposal](proposal, link, publicLink)
  }

  private def linked(group: Group, user: User)(implicit req: BasicReq[AnyContent]): Linked[User] = {
    val link = req.format(gospeak.web.pages.orga.speakers.routes.SpeakerCtrl.detail(group.slug, user.slug))
    val publicLink = if (user.isPublic) {
      Some(req.format(gospeak.web.pages.published.speakers.routes.SpeakerCtrl.detail(user.slug)))
    } else {
      user.website.map(_.value)
        .orElse(user.social.linkedIn.map(_.link))
        .orElse(user.social.twitter.map(_.link))
    }
    Linked[User](user, link, publicLink)
  }

  private def linked(cfp: ExternalCfp)(implicit req: BasicReq[AnyContent]): Linked[ExternalCfp] = {
    val publicLink = req.format(gospeak.web.pages.published.cfps.routes.CfpCtrl.detailExt(cfp.id))
    Linked[ExternalCfp](cfp, publicLink, Some(publicLink))
  }
}
