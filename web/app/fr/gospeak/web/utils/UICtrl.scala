package fr.gospeak.web.utils

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.core.domain._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.pages
import play.api.i18n.I18nSupport
import play.api.mvc._

abstract class UICtrl(cc: ControllerComponents, silhouette: Silhouette[CookieEnv]) extends AbstractController(cc) with I18nSupport {
  protected def userOpt(implicit req: UserAwareRequest[CookieEnv, AnyContent]): Option[User.Id] = req.identity.map(_.user.id)

  protected def user(implicit req: SecuredRequest[CookieEnv, AnyContent]): User.Id = req.identity.user.id

  // same as user method by with different semantic name
  protected def by(implicit req: SecuredRequest[CookieEnv, AnyContent]): User.Id = req.identity.user.id

  // orga redirects
  protected def groupNotFound(group: Group.Slug): Result =
    Redirect(pages.user.routes.UserCtrl.listGroup()).flashing("warning" -> s"Unable to find group with slug '${group.value}'")

  protected def eventNotFound(group: Group.Slug, event: Event.Slug): Result =
    Redirect(pages.orga.events.routes.EventCtrl.list(group)).flashing("warning" -> s"Unable to find event with slug '${event.value}'")

  protected def cfpNotFound(group: Group.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.orga.cfps.routes.CfpCtrl.list(group)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def cfpNotFound(group: Group.Slug, event: Event.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.orga.events.routes.EventCtrl.detail(group, event)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def proposalNotFound(group: Group.Slug, cfp: Cfp.Slug, proposal: Proposal.Id): Result =
    Redirect(pages.orga.cfps.proposals.routes.ProposalCtrl.list(group, cfp)).flashing("warning" -> s"Unable to find proposal with id '${proposal.value}'")

  protected def speakerNotFound(group: Group.Slug, speaker: User.Slug): Result =
    Redirect(pages.orga.speakers.routes.SpeakerCtrl.list(group)).flashing("warning" -> s"Unable to find speaker with slug '${speaker.value}'")

  protected def partnerNotFound(group: Group.Slug, partner: Partner.Slug): Result =
    Redirect(pages.orga.partners.routes.PartnerCtrl.list(group)).flashing("warning" -> s"Unable to find partner with slug '${partner.value}'")

  protected def venueNotFound(group: Group.Slug, venue: Venue.Id): Result =
    Redirect(pages.orga.venues.routes.VenueCtrl.list(group)).flashing("warning" -> s"Unable to find venue with id '${venue.value}'")

  protected def venueNotFound(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Result =
    Redirect(pages.orga.partners.routes.PartnerCtrl.detail(group, partner)).flashing("warning" -> s"Unable to find venue with id '${venue.value}'")

  // speaker redirects
  protected def talkNotFound(talk: Talk.Slug): Result =
    Redirect(pages.speaker.talks.routes.TalkCtrl.list()).flashing("warning" -> s"Unable to find talk with slug '${talk.value}'")

  protected def cfpNotFound(talk: Talk.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.speaker.talks.cfps.routes.CfpCtrl.list(talk)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def proposalNotFound(talk: Talk.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.speaker.talks.routes.TalkCtrl.detail(talk)).flashing("warning" -> s"Unable to find proposal for CFP '${cfp.value}'")

  // public redirects
  protected def publicCfpNotFound(cfp: Cfp.Slug): Result =
    Redirect(pages.published.cfps.routes.CfpCtrl.list()).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def publicGroupNotFound(group: Group.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.list()).flashing("warning" -> s"Unable to find group with slug '${group.value}'")

  protected def publicCfpNotFound(group: Group.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.detail(group)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def publicUserNotFound(user: User.Slug): Result =
    Redirect(pages.published.speakers.routes.SpeakerCtrl.list()).flashing("warning" -> s"Unable to find speaker with slug '${user.value}'")

  protected def notFound()(implicit req: Request[AnyContent]): Result =
    NotFound("Not found :(")
}
