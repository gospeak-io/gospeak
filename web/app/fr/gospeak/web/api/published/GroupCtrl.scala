package fr.gospeak.web.api.published

import cats.data.OptionT
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.storage.{PublicGroupRepo, PublicProposalRepo, PublicUserRepo, PublicVenueRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.api.domain.{PublicApiGroup, PublicApiProposal}
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class GroupCtrl(cc: ControllerComponents,
                groupRepo: PublicGroupRepo,
                proposalRepo: PublicProposalRepo,
                venueRepo: PublicVenueRepo,
                userRepo: PublicUserRepo) extends ApiCtrl(cc) {
  def list(params: Page.Params): Action[AnyContent] = Action.async { implicit req =>
    responsePage {
      groupRepo.list(params).map(_.map(PublicApiGroup(_)))
    }
  }

  def detail(group: Group.Slug): Action[AnyContent] = Action.async { implicit req =>
    response {
      groupRepo.find(group).map(_.map(PublicApiGroup(_)))
    }
  }

  // cf https://humantalksparis.herokuapp.com/api/talks?include=speaker,meetup,venue
  def talks(group: Group.Slug, params: Page.Params): Action[AnyContent] = Action.async { implicit req =>
    responsePageT {
      for {
        groupElt <- OptionT(groupRepo.find(group))
        talks <- OptionT.liftF(proposalRepo.listPublicFull(groupElt.id, params))
        speakers <- OptionT.liftF(userRepo.list(talks.items.flatMap(_.proposal.speakers.toList).distinct))
        venues <- OptionT.liftF(venueRepo.listFull(groupElt.id, talks.items.flatMap(_.event.flatMap(_.venue))))
      } yield talks.map(PublicApiProposal(_, speakers, venues))
    }
  }
}
