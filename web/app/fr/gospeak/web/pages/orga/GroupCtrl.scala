package fr.gospeak.web.pages.orga

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Group, UserRequest}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.emails.Emails
import fr.gospeak.web.domain._
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.utils.{HttpUtils, UICtrl}
import play.api.mvc._

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: OrgaUserRepo,
                groupRepo: OrgaGroupRepo,
                cfpRepo: OrgaCfpRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                proposalRepo: OrgaProposalRepo,
                sponsorRepo: OrgaSponsorRepo,
                sponsorPackRepo: OrgaSponsorPackRepo,
                partnerRepo: OrgaPartnerRepo,
                userRequestRepo: OrgaUserRequestRepo,
                emailSrv: EmailSrv) extends UICtrl(cc, silhouette) {

  import silhouette._

  def detail(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      events <- OptionT.liftF(eventRepo.listAfter(groupElt.id, now, Page.Params.defaults.orderBy("start")))
      cfps <- OptionT.liftF(cfpRepo.list(events.items.flatMap(_.cfp)))
      venues <- OptionT.liftF(venueRepo.list(groupElt.id, events.items.flatMap(_.venue)))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.users).distinct))
      sponsors <- OptionT.liftF(sponsorRepo.listAll(groupElt.id).map(_.groupBy(_.partner)))
      partners <- OptionT.liftF(partnerRepo.list(sponsors.keys.toSeq))
      currentSponsors = sponsors.flatMap { case (id, ss) => partners.find(_.id == id).flatMap(p => ss.find(_.isCurrent(now)).map(s => (p, (s, ss.length)))) }.toSeq.sortBy(_._2._1.finish.toEpochDay)
      pastSponsors = sponsors.filter(_._2.forall(!_.isCurrent(now))).flatMap { case (id, s) => partners.find(_.id == id).map(p => (p, s)) }.toSeq.sortBy(s => -s._2.map(_.finish.toEpochDay).max)
      packs <- OptionT.liftF(sponsorPackRepo.listAll(groupElt.id))
      requests <- OptionT.liftF(userRequestRepo.listPendingGroupRequests(groupElt.id, now))
      requestUsers <- OptionT.liftF(userRepo.list(requests.flatMap(_.users).distinct))
      b = breadcrumb(groupElt)
    } yield Ok(html.detail(groupElt, events, cfps, venues, proposals, speakers, currentSponsors, pastSponsors, packs, requests, requestUsers)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def acceptJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.GroupCtrl.detail(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(groupElt.id, userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.acceptUserToJoinAGroup(requestElt, user, now))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupAccepted(userElt, req.identity, groupElt)))
      msg = s"You accepted <b>${userElt.name.value}</b> as organizer of ${groupElt.name.value}"
    } yield next.flashing("success" -> msg)).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }

  def rejectJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.GroupCtrl.detail(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(groupElt.id, userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.rejectUserToJoinAGroup(requestElt, user, now))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupRejected(userElt, req.identity, groupElt)))
      msg = s"You refused to <b>${userElt.name.value}</b> to join ${groupElt.name.value} as an organizer"
    } yield next.flashing("error" -> msg)).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def breadcrumb(group: Group): Breadcrumb =
    Breadcrumb(group.name.value -> routes.GroupCtrl.detail(group.slug))
}
