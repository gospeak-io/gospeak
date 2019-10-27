package fr.gospeak.web.domain

import java.time.LocalDateTime

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.GospeakMessage.Linked
import fr.gospeak.core.domain.utils.{GospeakMessage, TemplateData}
import fr.gospeak.web.auth.domain.{AuthUser, CookieEnv}
import fr.gospeak.web.services.EventSrv.EventFull
import play.api.mvc.{AnyContent, RequestHeader}

class MessageBuilder {
  def buildEventCreated(group: Group, event: Event)(implicit req: SecuredRequest[CookieEnv, AnyContent]): GospeakMessage.EventCreated = {
    GospeakMessage.EventCreated(linked(group), linked(group, event), req.identity.user)
  }

  def buildTalkAdded(group: Group, event: Event, cfp: Cfp, proposal: Proposal, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): GospeakMessage.TalkAdded = {
    GospeakMessage.TalkAdded(linked(group), linked(group, event), linked(group, cfp, now), linked(group, event, cfp, proposal), req.identity.user)
  }

  def buildTalkRemoved(group: Group, event: Event, cfp: Cfp, proposal: Proposal, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): GospeakMessage.TalkRemoved = {
    GospeakMessage.TalkRemoved(linked(group), linked(group, event), linked(group, cfp, now), linked(group, event, cfp, proposal), req.identity.user)
  }

  def buildProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): GospeakMessage.ProposalCreated = {
    GospeakMessage.ProposalCreated(linked(group), linked(group, cfp, now), linked(group, cfp, proposal), req.identity.user)
  }

  def buildProposalCreated(group: Group, cfp: Cfp, proposal: Proposal, identity: AuthUser, now: LocalDateTime)(implicit req: UserAwareRequest[CookieEnv, AnyContent]): GospeakMessage.ProposalCreated = {
    GospeakMessage.ProposalCreated(linked(group), linked(group, cfp, now), linked(group, cfp, proposal), identity.user)
  }

  def buildEventInfo(group: Group, event: Event, cfpOpt: Option[Cfp], venueOpt: Option[Venue.Full], talks: Seq[Proposal], speakers: Seq[User], now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): TemplateData.EventInfo = {
    TemplateData.eventInfo(
      g = linked(group),
      e = linked(group, event),
      v = venueOpt,
      c = cfpOpt.map(linked(group, _, now)),
      ts = cfpOpt.map(cfp => talks.map(linked(group, event, cfp, _))).getOrElse(Seq()),
      ss = speakers.map(linked(group, _)))
  }

  def buildEventInfo(i: EventFull, now: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): TemplateData.EventInfo =
    buildEventInfo(i.group, i.event, i.cfpOpt, i.venueOpt, i.talks, i.speakers, now)

  private def linked(group: Group)(implicit req: RequestHeader): Linked[Group] = {
    val link = fr.gospeak.web.pages.orga.routes.GroupCtrl.detail(group.slug).absoluteURL()
    val publicLink = Some(fr.gospeak.web.pages.published.groups.routes.GroupCtrl.detail(group.slug).absoluteURL())
    Linked[Group](group, link, publicLink)
  }

  private def linked(group: Group, event: Event)(implicit req: RequestHeader): Linked[Event] = {
    val link = fr.gospeak.web.pages.orga.events.routes.EventCtrl.detail(group.slug, event.slug).absoluteURL()
    val publicLink = if (event.isPublic) {
      Some(fr.gospeak.web.pages.published.groups.routes.GroupCtrl.event(group.slug, event.slug).absoluteURL())
    } else {
      None
    }
    Linked[Event](event, link, publicLink)
  }

  private def linked(group: Group, cfp: Cfp, now: LocalDateTime)(implicit req: RequestHeader): Linked[Cfp] = {
    val link = fr.gospeak.web.pages.orga.cfps.routes.CfpCtrl.detail(group.slug, cfp.slug).absoluteURL()
    val publicLink = if (cfp.isActive(now)) {
      Some(fr.gospeak.web.pages.published.cfps.routes.CfpCtrl.detail(cfp.slug).absoluteURL())
    } else {
      None
    }
    Linked[Cfp](cfp, link, publicLink)
  }

  private def linked(group: Group, cfp: Cfp, proposal: Proposal)(implicit req: RequestHeader): Linked[Proposal] = {
    val link = fr.gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id).absoluteURL()
    Linked[Proposal](proposal, link, None)
  }

  private def linked(group: Group, event: Event, cfp: Cfp, proposal: Proposal)(implicit req: RequestHeader): Linked[Proposal] = {
    val link = fr.gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl.detail(group.slug, cfp.slug, proposal.id).absoluteURL()
    val publicLink = if (event.isPublic) {
      Some(fr.gospeak.web.pages.published.groups.routes.GroupCtrl.talk(group.slug, proposal.id).absoluteURL())
    } else {
      None
    }
    Linked[Proposal](proposal, link, publicLink)
  }

  private def linked(group: Group, user: User)(implicit req: RequestHeader): Linked[User] = {
    val link = fr.gospeak.web.pages.orga.speakers.routes.SpeakerCtrl.detail(group.slug, user.slug).absoluteURL()
    val publicLink = if (user.isPublic) {
      Some(fr.gospeak.web.pages.published.speakers.routes.SpeakerCtrl.detail(user.slug).absoluteURL())
    } else {
      user.profile.website.map(_.value)
        .orElse(user.profile.linkedin.map(_.value))
        .orElse(user.profile.twitter.map(_.value))
    }
    Linked[User](user, link, publicLink)
  }
}
