package fr.gospeak.web.pages.orga

import java.time.Instant

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.Group
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.utils.UICtrl
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
                partnerRepo: OrgaPartnerRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def detail(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      events <- OptionT.liftF(eventRepo.listAfter(groupElt.id, now, Page.Params.defaults.orderBy("start")))
      cfps <- OptionT.liftF(cfpRepo.list(events.items.flatMap(_.cfp)))
      venues <- OptionT.liftF(venueRepo.list(groupElt.id, events.items.flatMap(_.venue)))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.users)))
      sponsors <- OptionT.liftF(sponsorRepo.listAll(groupElt.id).map(_.groupBy(_.partner)))
      partners <- OptionT.liftF(partnerRepo.list(sponsors.keys.toSeq))
      currentSponsors = sponsors.flatMap { case (id, ss) => partners.find(_.id == id).flatMap(p => ss.find(_.isCurrent(now)).map(s => (p, (s, ss.length)))) }
      pastSponsors = sponsors.filter(_._2.exists(!_.isCurrent(now))).flatMap { case (id, s) => partners.find(_.id == id).map(p => (p, s)) }
      packs <- OptionT.liftF(sponsorPackRepo.listAll(groupElt.id))
      b = breadcrumb(groupElt)
    } yield Ok(html.detail(groupElt, events, cfps, venues, proposals, speakers, currentSponsors, pastSponsors, packs)(b))).value.map(_.getOrElse(groupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def breadcrumb(group: Group): Breadcrumb =
    Breadcrumb(group.name.value -> routes.GroupCtrl.detail(group.slug))
}
