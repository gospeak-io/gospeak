package fr.gospeak.web.api.published

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Event, Group, Proposal}
import fr.gospeak.core.services.meetup.MeetupSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.utils.ApiResult
import fr.gospeak.web.api.domain._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                groupRepo: PublicGroupRepo,
                eventRepo: PublicEventRepo,
                proposalRepo: PublicProposalRepo,
                venueRepo: PublicVenueRepo,
                userRepo: PublicUserRepo,
                groupSettingsRepo: PublicGroupSettingsRepo,
                meetupSrv: MeetupSrv) extends ApiCtrl(cc, silhouette, conf) {
  def list(params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiGroup.Published]] { implicit req =>
    groupRepo.listFull(params).map(ApiResult.of(_, ApiGroup.published))
  }

  def detail(group: Group.Slug): Action[AnyContent] = UserAwareAction[ApiGroup.Published] { implicit req =>
    groupRepo.findFull(group).map(_.map(g => ApiResult.of(ApiGroup.published(g))).getOrElse(groupNotFound(group)))
  }

  def events(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiEvent.Published]] { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      events <- OptionT.liftF(eventRepo.listPublished(groupElt.id, params))
      talks <- OptionT.liftF(proposalRepo.listPublic(events.items.flatMap(_.talks).distinct))
      speakers <- OptionT.liftF(userRepo.list(talks.flatMap(_.speakers.toList).distinct))
      res = ApiResult.of(events, ApiEvent.published(_, talks, speakers))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  }

  def event(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAwareAction[ApiEvent.Published] { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      talks <- OptionT.liftF(proposalRepo.listPublic(eventElt.talks.distinct))
      speakers <- OptionT.liftF(userRepo.list(talks.flatMap(_.speakers.toList).distinct))
      res = ApiResult.of(ApiEvent.published(eventElt, talks, speakers))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def eventAttendeesMeetup(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAwareAction[Seq[ApiAttendee.Published]] { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      creds <- OptionT(groupSettingsRepo.findMeetup(groupElt.id))
      attendees <- OptionT(eventElt.event.refs.meetup.map(r => meetupSrv.getAttendees(r.group, r.event, conf.app.aesKey, creds)).sequence)
      host = req.getQueryString("host")
      response = req.getQueryString("response")
      cleanAttendees = attendees.filter(a => a.id.value != 0L && response.forall(_ == a.response) && host.forall(_ == a.host.toString))
      res = ApiResult.of(cleanAttendees.map(ApiAttendee.published(_, creds.group)))
    } yield res).value.map(_.getOrElse(eventNotFound(group, event)))
  }

  def talks(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiProposal.Published]] { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      talks <- OptionT.liftF(proposalRepo.listPublicFull(groupElt.id, params))
      speakers <- OptionT.liftF(userRepo.list(talks.items.flatMap(_.speakers.toList).distinct))
      res = ApiResult.of(talks, ApiProposal.published(_, speakers))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  }

  def talk(group: Group.Slug, talk: Proposal.Id): Action[AnyContent] = UserAwareAction[ApiProposal.Published] { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      talkElt <- OptionT(proposalRepo.findPublicFull(groupElt.id, talk))
      speakers <- OptionT.liftF(userRepo.list(talkElt.speakers.toList.distinct))
      res = ApiResult.of(ApiProposal.published(talkElt, speakers))
    } yield res).value.map(_.getOrElse(talkNotFound(group, talk)))
  }

  def speakers(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction[Seq[ApiUser.Published]] { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      speakers <- OptionT.liftF(userRepo.speakersPublic(groupElt.id, params))
      // TODO add proposals, talks, groups member and owners for each speaker
      res = ApiResult.of(speakers, ApiUser.published)
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  }
}
