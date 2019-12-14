package fr.gospeak.web.api.published

import cats.data.OptionT
import fr.gospeak.core.domain.{Event, Group, Proposal}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.{PublicApiEvent, PublicApiGroup, PublicApiProposal, PublicApiSpeaker}
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class GroupCtrl(cc: ControllerComponents,
                conf: AppConf,
                groupRepo: PublicGroupRepo,
                eventRepo: PublicEventRepo,
                proposalRepo: PublicProposalRepo,
                venueRepo: PublicVenueRepo,
                userRepo: PublicUserRepo) extends ApiCtrl(cc, conf) {
  def list(params: Page.Params): Action[AnyContent] = ApiActionPage { implicit req =>
    groupRepo.list(params).map(_.map(PublicApiGroup(_)))
  }

  def detail(group: Group.Slug): Action[AnyContent] = ApiActionOpt { implicit req =>
    groupRepo.find(group).map(_.map(PublicApiGroup(_)))
  }

  def events(group: Group.Slug, params: Page.Params): Action[AnyContent] = ApiActionPageT { implicit req =>
    for {
      groupElt <- OptionT(groupRepo.find(group))
      events <- OptionT.liftF(eventRepo.listPublished(groupElt.id, params))
      talks <- OptionT.liftF(proposalRepo.listPublicFull(events.items.flatMap(_.talks).distinct))
      speakers <- OptionT.liftF(userRepo.list(talks.flatMap(_.speakers.toList).distinct))
    } yield events.map(PublicApiEvent(_, talks, speakers))
  }

  def event(group: Group.Slug, event: Event.Slug): Action[AnyContent] = ApiActionOptT { implicit req =>
    for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      talks <- OptionT.liftF(proposalRepo.listPublicFull(eventElt.talks.distinct))
      speakers <- OptionT.liftF(userRepo.list(talks.flatMap(_.speakers.toList).distinct))
    } yield PublicApiEvent(eventElt, talks, speakers)
  }

  def talks(group: Group.Slug, params: Page.Params): Action[AnyContent] = ApiActionPageT { implicit req =>
    for {
      groupElt <- OptionT(groupRepo.find(group))
      talks <- OptionT.liftF(proposalRepo.listPublicFull(groupElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(talks.items.flatMap(_.speakers.toList).distinct))
      venues <- OptionT.liftF(venueRepo.listFull(groupElt.id, talks.items.flatMap(_.event.flatMap(_.venue)).distinct))
    } yield talks.map(PublicApiProposal(_, speakers, venues))
  }

  def talk(group: Group.Slug, talk: Proposal.Id): Action[AnyContent] = ApiActionOptT { implicit req =>
    for {
      groupElt <- OptionT(groupRepo.find(group))
      talkElt <- OptionT(proposalRepo.findPublicFull(groupElt.id, talk))
      speakers <- OptionT.liftF(userRepo.list(talkElt.speakers.toList.distinct))
      venues <- OptionT.liftF(venueRepo.listFull(groupElt.id, talkElt.event.flatMap(_.venue).toList))
    } yield PublicApiProposal(talkElt, speakers, venues)
  }

  def speakers(group: Group.Slug, params: Page.Params): Action[AnyContent] = ApiActionPageT { implicit req =>
    for {
      groupElt <- OptionT(groupRepo.find(group))
      speakers <- OptionT.liftF(userRepo.speakersPublic(groupElt.id, params))
      // TODO add proposals, talks, groups member and owners for each speaker
    } yield speakers.map(PublicApiSpeaker(_))
  }
}
