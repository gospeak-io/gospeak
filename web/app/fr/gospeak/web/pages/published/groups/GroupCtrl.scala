package fr.gospeak.web.pages.published.groups

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Event, Group, Proposal}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl
import fr.gospeak.web.pages.published.groups.GroupCtrl._
import fr.gospeak.web.utils.UICtrl
import play.api.mvc._

import scala.util.control.NonFatal

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                userRepo: PublicUserRepo,
                groupRepo: PublicGroupRepo,
                cfpRepo: PublicCfpRepo,
                eventRepo: PublicEventRepo,
                proposalRepo: PublicProposalRepo,
                sponsorRepo: PublicSponsorRepo,
                sponsorPackRepo: PublicSponsorPackRepo) extends UICtrl(cc, silhouette) {

  import silhouette._

  def list(params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groups <- groupRepo.list(params)
      b = listBreadcrumb()
    } yield Ok(html.list(groups)(b))).unsafeToFuture()
  }

  def detail(group: Group.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      cfps <- OptionT.liftF(cfpRepo.listAllOpen(groupElt.id, now))
      events <- OptionT.liftF(eventRepo.listPublished(groupElt.id, Page.Params.defaults))
      sponsors <- OptionT.liftF(sponsorRepo.listCurrentFull(groupElt.id, now))
      packs <- OptionT.liftF(sponsorPackRepo.listActives(groupElt.id))
      member <- OptionT.liftF(userOpt.map(groupRepo.findActiveMember(groupElt.id, _)).sequence.map(_.flatten))
      b = breadcrumb(groupElt)
      res = Ok(html.detail(groupElt, cfps, events, sponsors, packs, member)(b))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }

  def doJoin(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      member <- OptionT.liftF(groupRepo.findActiveMember(groupElt.id, user))
      msg <- OptionT.liftF(member.swap.map(_ => groupRepo.join(groupElt.id)(req.identity.user, now)
        .map(_ => "success" -> s"You are now a member of <b>${groupElt.name.value}</b>")
        .recover { case NonFatal(e) => "error" -> s"Can't join <b>${groupElt.name.value}</b>: ${e.getMessage}" })
        .getOrElse(IO.pure("success" -> s"You are already a member of <b>${groupElt.name.value}</b>")))
      next = Redirect(routes.GroupCtrl.detail(group)).flashing(msg)
    } yield next).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }

  def doLeave(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      member <- OptionT.liftF(groupRepo.findActiveMember(groupElt.id, user))
      msg <- OptionT.liftF(member.map(groupRepo.leave(_)(user, now)
        .map(_ => "success" -> s"You have leaved <b>${groupElt.name.value}</b>")
        .recover { case NonFatal(e) => "error" -> s"Can't leave <b>${groupElt.name.value}</b>, error: ${e.getMessage}" })
        .getOrElse(IO.pure("error" -> s"You are not a member of <b>${groupElt.name.value}</b>")))
      next = Redirect(routes.GroupCtrl.detail(group)).flashing(msg)
    } yield next).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }

  def events(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      events <- OptionT.liftF(eventRepo.listPublished(groupElt.id, params))
      b = breadcrumbEvents(groupElt)
      res = Ok(html.events(groupElt, events)(b))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }

  def event(group: Group.Slug, event: Event.Slug): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      eventElt <- OptionT(eventRepo.findPublished(groupElt.id, event))
      proposals <- OptionT.liftF(proposalRepo.listPublicFull(eventElt.talks))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.speakers.toList).distinct))
      b = breadcrumbEvent(groupElt, eventElt)
      res = Ok(html.event(groupElt, eventElt, proposals, speakers)(b))
    } yield res).value.map(_.getOrElse(publicEventNotFound(group, event))).unsafeToFuture()
  }

  def talks(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      proposals <- OptionT.liftF(proposalRepo.listPublicFull(groupElt.id, params.defaultOrderBy("title")))
      speakers <- OptionT.liftF(userRepo.list(proposals.items.flatMap(_.speakers.toList).distinct))
      b = breadcrumbTalks(groupElt)
      res = Ok(html.talks(groupElt, proposals, speakers)(b))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }

  def talk(group: Group.Slug, proposal: Proposal.Id): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      proposalElt <- OptionT(proposalRepo.findPublicFull(groupElt.id, proposal))
      speakers <- OptionT.liftF(userRepo.list(proposalElt.speakers.toList))
      b = breadcrumbTalk(groupElt, proposalElt)
      res = Ok(html.talk(groupElt, proposalElt, speakers)(b))
    } yield res).value.map(_.getOrElse(publicProposalNotFound(group, proposal))).unsafeToFuture()
  }

  def speakers(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAwareAction.async { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      speakers <- OptionT.liftF(userRepo.speakers(groupElt.id, params))
      b = breadcrumbSpeakers(groupElt)
      res = Ok(html.speakers(groupElt, speakers)(b))
    } yield res).value.map(_.getOrElse(publicGroupNotFound(group))).unsafeToFuture()
  }
}

object GroupCtrl {
  def listBreadcrumb(): Breadcrumb =
    HomeCtrl.breadcrumb().add("Groups" -> routes.GroupCtrl.list())

  def breadcrumb(group: Group): Breadcrumb =
    listBreadcrumb().add(group.name.value -> routes.GroupCtrl.detail(group.slug))

  def breadcrumbEvents(group: Group): Breadcrumb =
    breadcrumb(group).add("Events" -> routes.GroupCtrl.events(group.slug))

  def breadcrumbEvent(group: Group, event: Event.Full): Breadcrumb =
    breadcrumbEvents(group).add(event.name.value -> routes.GroupCtrl.event(group.slug, event.slug))

  def breadcrumbTalks(group: Group): Breadcrumb =
    breadcrumb(group).add("Talks" -> routes.GroupCtrl.talks(group.slug))

  def breadcrumbTalk(group: Group, proposal: Proposal.Full): Breadcrumb =
    breadcrumbTalks(group).add(proposal.title.value -> routes.GroupCtrl.talk(group.slug, proposal.id))

  def breadcrumbSpeakers(group: Group): Breadcrumb =
    breadcrumb(group).add("Speakers" -> routes.GroupCtrl.speakers(group.slug))
}
