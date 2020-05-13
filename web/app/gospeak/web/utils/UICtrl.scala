package gospeak.web.utils

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.services.storage.OrgaGroupRepo
import gospeak.libs.scala.Extensions._
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.{AppConf, pages}
import org.h2.jdbc.{JdbcSQLIntegrityConstraintViolationException, JdbcSQLSyntaxErrorException}
import org.slf4j.LoggerFactory
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.Future
import scala.util.control.NonFatal

abstract class UICtrl(cc: ControllerComponents,
                      silhouette: Silhouette[CookieEnv],
                      conf: AppConf) extends AbstractController(cc) with I18nSupport {
  private val logger = LoggerFactory.getLogger(this.getClass)

  protected def UserAwareAction(block: UserAwareReq[AnyContent] => IO[Result]): Action[AnyContent] = silhouette.UserAwareAction.async { r =>
    implicit val req: UserAwareReq[AnyContent] = UserAwareReq.from(conf, messagesApi, r)
    actionResult(block(req))
  }

  protected def UserAction(block: UserReq[AnyContent] => IO[Result]): Action[AnyContent] = silhouette.SecuredAction.async { r =>
    implicit val req: UserReq[AnyContent] = UserReq.from(conf, messagesApi, r)
    actionResult(block(req))
  }

  private def actionResult(result: IO[Result])(implicit req: BasicReq[AnyContent]): Future[Result] = {
    def logError(e: Throwable, msg: String = ""): Unit = {
      val (user, group) = req match {
        case r: OrgaReq[AnyContent] => Some(r.user) -> Some(r.group)
        case r: UserReq[AnyContent] => Some(r.user) -> None
        case r: UserAwareReq[AnyContent] => r.user -> None
      }
      val userStr = user.map(u => s", user ${u.name.value} (${u.id.value})").getOrElse("")
      val groupStr = group.map(g => s", group ${g.name.value} (${g.id.value})").getOrElse("")
      val msgStr = if (msg.isEmpty) "" else s": $msg"
      // FIXME better error handling (send email or notif?)
      logger.error(s"Error in controller for request ${req.customId}" + userStr + groupStr + msgStr, e)
    }

    val next = redirectToPreviousPageOr(gospeak.web.pages.published.routes.HomeCtrl.index())
    result.recover {
      case e: JdbcSQLSyntaxErrorException =>
        logError(e, s"\n  Message: ${e.getOriginalMessage}\n  SQL: ${e.getSQL}")
        next.flashing("error" -> s"Unexpected SQL error for request ${req.customId}")
      case e: JdbcSQLIntegrityConstraintViolationException =>
        logError(e, s"\n  Message: ${e.getOriginalMessage}\n  SQL: ${e.getSQL}")
        next.flashing("error" -> s"Duplicate key SQL error for request ${req.customId}")
      case NonFatal(e) =>
        logError(e)
        next.flashing("error" -> s"Unexpected error for request ${req.customId} (${e.getClass.getSimpleName}):<br>${e.getMessage}")
    }.unsafeToFuture()
  }

  protected def redirectOr[A](redirect: Option[String], default: => Call)(implicit req: Request[A]): Result =
    redirect
      .filterNot(url => url.contains("login") || url.contains("signup"))
      .map(Redirect(_))
      .getOrElse(Redirect(default))

  protected def redirectToPreviousPageOr[A](default: => Call)(implicit req: Request[A]): Result =
    redirectOr(HttpUtils.getReferer(req.headers), default)

  protected def redirectToPreviousPageOr[A](redirect: Option[String], default: => Call)(implicit req: Request[A]): Result =
    redirectOr(HttpUtils.getReferer(req.headers).orElse(redirect), default)

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

  protected def venueNotFound(group: Group.Slug, partner: Partner.Slug, venue: Venue.Id): Result =
    Redirect(pages.orga.partners.routes.PartnerCtrl.detail(group, partner)).flashing("warning" -> s"Unable to find venue with id '${venue.value}'")

  protected def packNotFound(group: Group.Slug, pack: SponsorPack.Slug): Result =
    Redirect(pages.orga.sponsors.routes.SponsorCtrl.list(group)).flashing("warning" -> s"Unable to find sponsoring pack with slug '${pack.value}'")

  protected def sponsorNotFound(group: Group.Slug, sponsor: Sponsor.Id): Result =
    Redirect(pages.orga.sponsors.routes.SponsorCtrl.list(group)).flashing("warning" -> s"Unable to find sponsor with id '${sponsor.value}'")

  protected def contactNotFound(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Result =
    Redirect(pages.orga.partners.routes.PartnerCtrl.detail(group, partner)).flashing("warning" -> s"Unable to find contact with id '${contact.value}'")

  // user redirects
  protected def talkNotFound(talk: Talk.Slug): Result =
    Redirect(pages.user.talks.routes.TalkCtrl.list()).flashing("warning" -> s"Unable to find talk with slug '${talk.value}'")

  protected def extEventNotFound(talk: Talk.Slug, event: ExternalEvent.Id): Result =
    Redirect(pages.user.talks.routes.TalkCtrl.detail(talk)).flashing("warning" -> s"Unable to find this event")

  protected def extEventNotFound(event: ExternalEvent.Id): Result =
    Redirect(pages.published.events.routes.EventCtrl.list()).flashing("warning" -> s"Unable to find this event")

  protected def extCfpNotFound(cfp: ExternalCfp.Id): Result =
    Redirect(pages.published.cfps.routes.CfpCtrl.list()).flashing("warning" -> s"Unable to find this CFP")

  protected def extProposalNotFound(talk: Talk.Slug, proposal: ExternalProposal.Id): Result =
    Redirect(pages.user.talks.routes.TalkCtrl.detail(talk)).flashing("warning" -> s"Unable to find this proposal")

  protected def cfpNotFound(talk: Talk.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.user.talks.cfps.routes.CfpCtrl.list(talk)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def proposalNotFound(talk: Talk.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.user.talks.routes.TalkCtrl.detail(talk)).flashing("warning" -> s"Unable to find proposal for CFP '${cfp.value}'")

  // public redirects
  protected def publicCfpNotFound(cfp: Cfp.Slug): Result =
    Redirect(pages.published.cfps.routes.CfpCtrl.list()).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def publicCfpNotFound(cfp: ExternalCfp.Id): Result =
    Redirect(pages.published.cfps.routes.CfpCtrl.list()).flashing("warning" -> s"Unable to find CFP")

  protected def publicGroupNotFound(group: Group.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.list()).flashing("warning" -> s"Unable to find group with slug '${group.value}'")

  protected def publicEventNotFound(event: ExternalEvent.Id): Result =
    Redirect(pages.published.events.routes.EventCtrl.list()).flashing("warning" -> s"Unable to find event")

  protected def publicEventNotFound(group: Group.Slug, event: Event.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.events(group)).flashing("warning" -> s"Unable to find event with slug '${event.value}'")

  protected def publicProposalNotFound(group: Group.Slug, proposal: Proposal.Id): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.talks(group)).flashing("warning" -> s"Unable to find talk with id '${proposal.value}'")

  protected def publicProposalNotFound(event: ExternalEvent.Id, proposal: ExternalProposal.Id): Result =
    Redirect(pages.published.events.routes.EventCtrl.detailExt(event)).flashing("warning" -> s"Unable to find proposal")

  protected def publicCfpNotFound(group: Group.Slug, cfp: Cfp.Slug): Result =
    Redirect(pages.published.groups.routes.GroupCtrl.detail(group)).flashing("warning" -> s"Unable to find CFP with slug '${cfp.value}'")

  protected def publicUserNotFound(user: User.Slug): Result =
    Redirect(pages.published.speakers.routes.SpeakerCtrl.list()).flashing("warning" -> s"Unable to find speaker with slug '${user.value}'")

  protected def publicTalkNotFound(user: User.Slug, talk: Talk.Slug): Result =
    Redirect(pages.published.speakers.routes.SpeakerCtrl.detail(user)).flashing("warning" -> s"Unable to find talk with slug '${talk.value}'")

  protected def publicVideoNotFound(video: Video.Id): Result =
    Redirect(pages.published.videos.routes.VideoCtrl.list()).flashing("warning" -> s"Unable to find video with id '${video.value}'")

  protected def notFound()(implicit req: UserAwareReq[AnyContent]): Result =
    NotFound("Not found :(")
}

object UICtrl {
  private val home = pages.published.routes.HomeCtrl.index()
  private val userHome = pages.user.routes.UserCtrl.index()

  trait Auth {
    self: UICtrl =>

    def loggedRedirect(result: => IO[Result], redirect: Option[String])(implicit req: UserAwareReq[AnyContent]): IO[Result] = {
      req.secured.map(r => loggedRedirect(redirect)(r)).getOrElse(result)
    }

    def loggedRedirect(redirect: Option[String])(implicit req: UserReq[AnyContent]): IO[Result] = {
      IO.pure(redirect.map(Redirect(_))
        .orElse(if (req.groups.length == 1) Some(Redirect(pages.orga.routes.GroupCtrl.detail(req.groups.head.slug))) else None)
        .getOrElse(Redirect(userHome)))
        .map(_.flashing(req.flash))
    }

    def logoutRedirect(implicit req: UserReq[AnyContent]): IO[Result] = {
      IO.pure(Redirect(home))
    }
  }

  trait OrgaAction {
    self: UICtrl =>
    val groupRepo: OrgaGroupRepo

    protected def OrgaAction(group: Group.Slug)(block: OrgaReq[AnyContent] => IO[Result]): Action[AnyContent] = {
      UserAction { req =>
        groupRepo.find(group).flatMap {
          case Some(groupElt) if groupElt.owners.toList.contains(req.user.id) => block(req.orga(groupElt))
          case Some(_) => IO.pure(Redirect(userHome).flashing("warning" -> s"You are not a '${group.value}' group owner"))
          case None => IO.pure(Redirect(userHome).flashing("warning" -> s"Unable to find group with slug '${group.value}'"))
        }
      }
    }
  }

  trait AdminAction {
    self: UICtrl =>

    protected def AdminAction(block: AdminReq[AnyContent] => IO[Result]): Action[AnyContent] = {
      UserAction { req =>
        if (req.isAdmin) block(req.admin) else IO.pure(Redirect(userHome).flashing("warning" -> s"Restricted to admins"))
      }
    }
  }

}
