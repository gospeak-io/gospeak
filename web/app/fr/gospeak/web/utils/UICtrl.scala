package fr.gospeak.web.utils

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.pages
import org.h2.jdbc.JdbcSQLSyntaxErrorException
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.util.control.NonFatal

abstract class UICtrl(cc: ControllerComponents,
                      silhouette: Silhouette[CookieEnv],
                      env: ApplicationConf.Env) extends AbstractController(cc) with I18nSupport {
  protected def UserAwareActionIO(block: UserAwareReq[AnyContent] => IO[Result]): Action[AnyContent] = silhouette.UserAwareAction.async { r =>
    val req = UserAwareReq[AnyContent](r, messagesApi.preferred(r.request), env)
    recoverFailedAction(block(req))(r.request).unsafeToFuture()
  }

  protected def SecuredActionIO(block: SecuredReq[AnyContent] => IO[Result]): Action[AnyContent] = silhouette.SecuredAction.async { r =>
    val req = SecuredReq[AnyContent](r, messagesApi.preferred(r.request), env)
    recoverFailedAction(block(req))(r.request).unsafeToFuture()
  }

  private def recoverFailedAction(result: IO[Result])(implicit req: Request[AnyContent]): IO[Result] = {
    val back = HttpUtils.getReferer(req.headers).getOrElse(fr.gospeak.web.pages.published.routes.HomeCtrl.index().toString)
    // FIXME better error handling (send email or notif?)
    result.recover {
      case e: JdbcSQLSyntaxErrorException => e.printStackTrace(); Redirect(back).flashing("error" -> s"Unexpected SQL error")
      case NonFatal(e) => e.printStackTrace(); Redirect(back).flashing("error" -> s"Unexpected error: ${e.getMessage} (${e.getClass.getSimpleName})")
    }
  }

  // orga redirects
  protected def groupNotFound(group: Group.Slug): Result =
    Redirect(pages.user.routes.UserCtrl.index()).flashing("warning" -> s"Unable to find group with slug '${group.value}'")

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

  protected def packNotFound(group: Group.Slug, pack: SponsorPack.Slug): Result =
    Redirect(pages.orga.sponsors.routes.SponsorCtrl.list(group)).flashing("warning" -> s"Unable to find sponsoring pack with slug '${pack.value}'")

  protected def sponsorNotFound(group: Group.Slug, sponsor: Sponsor.Id): Result =
    Redirect(pages.orga.sponsors.routes.SponsorCtrl.list(group)).flashing("warning" -> s"Unable to find sponsor with id '${sponsor.value}'")

  protected def contactNotFound(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Result =
    Redirect(pages.orga.partners.routes.PartnerCtrl.detail(group, partner)).flashing("warning" -> s"Unable to find contact with id '${contact.value}'")

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

  protected def publicCfpNotFound(cfp: ExternalCfp.Id): Result =
    Redirect(pages.published.cfps.routes.CfpCtrl.list()).flashing("warning" -> s"Unable to find CFP")

  protected def publicGroupNotFound(group: Group.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.list()).flashing("warning" -> s"Unable to find group with slug '${group.value}'")

  protected def publicEventNotFound(group: Group.Slug, event: Event.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.events(group)).flashing("warning" -> s"Unable to find event with slug '${event.value}'")

  protected def publicProposalNotFound(group: Group.Slug, proposal: Proposal.Id): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.talks(group)).flashing("warning" -> s"Unable to find talk with id '${proposal.value}'")

  protected def publicCfpNotFound(group: Group.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.detail(group)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def publicUserNotFound(user: User.Slug): Result =
    Redirect(pages.published.speakers.routes.SpeakerCtrl.list()).flashing("warning" -> s"Unable to find speaker with slug '${user.value}'")

  protected def notFound()(implicit req: UserAwareReq[AnyContent]): Result =
    NotFound("Not found :(")
}
