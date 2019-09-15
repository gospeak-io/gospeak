package fr.gospeak.web.pages.orga

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain.{Group, User, UserRequest}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.pages.orga.settings.SettingsCtrl
import fr.gospeak.web.pages.orga.settings.routes.{SettingsCtrl => SettingsRoutes}
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{HttpUtils, UICtrl}
import play.api.data.Form
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

  def create(): Action[AnyContent] = SecuredAction.async { implicit req =>
    createForm(GroupForms.create).unsafeToFuture()
  }

  def doCreate(): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => for {
        // TODO check if slug not already exist
        _ <- groupRepo.create(data, by, now)
      } yield Redirect(routes.GroupCtrl.detail(data.slug))
    ).unsafeToFuture()
  }

  private def createForm(form: Form[Group.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    val b = groupBreadcrumb(req.identity.user).add("New" -> routes.GroupCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def join(params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    (for {
      groups <- groupRepo.listJoinable(user, params)
      pendingRequests <- userRequestRepo.listPendingUserToJoinAGroupRequests(user)
      owners <- userRepo.list(groups.items.flatMap(_.owners.toList).distinct)
      b = groupBreadcrumb(req.identity.user).add("Join" -> routes.GroupCtrl.join())
    } yield Ok(html.join(groups, owners, pendingRequests)(b))).unsafeToFuture()
  }

  def doJoin(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      _ <- OptionT.liftF(userRequestRepo.createUserAskToJoinAGroup(user, groupElt.id, now))
    } yield Redirect(UserRoutes.index()).flashing("success" -> s"Join request sent to <b>${groupElt.name.value}</b> group"))
      .value.map(_.getOrElse(Redirect(routes.GroupCtrl.join(params)).flashing("error" -> s"Unable to send join request to <b>$group</b>"))).unsafeToFuture()
  }

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

  def edit(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    editForm(group, GroupForms.create).unsafeToFuture()
  }

  def doEdit(group: Group.Slug): Action[AnyContent] = SecuredAction.async { implicit req =>
    val now = Instant.now()
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => editForm(group, formWithErrors),
      data => (for {
        _ <- OptionT(groupRepo.find(user, group)) // to check that user is a group owner
        newSlugExits <- OptionT.liftF(groupRepo.exists(data.slug))
        res <- OptionT.liftF(
          if (newSlugExits && data.slug != group) {
            editForm(group, GroupForms.create.fillAndValidate(data).withError("slug", s"Slug ${data.slug.value} already taken by an other group"))
          } else {
            groupRepo.edit(group)(data, by, now).map { _ => Redirect(SettingsRoutes.settings(data.slug)) }
          })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    ).unsafeToFuture()
  }

  private def editForm(group: Group.Slug, form: Form[Group.Data])(implicit req: SecuredRequest[CookieEnv, AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      b = SettingsCtrl.listBreadcrumb(groupElt).add("Edit group" -> routes.GroupCtrl.edit(group))
      filledForm = if (form.hasErrors) form else form.fill(groupElt.data)
    } yield Ok(html.edit(groupElt, filledForm)(b))).value.map(_.getOrElse(groupNotFound(group)))
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
  def groupBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> UserRoutes.index())

  def breadcrumb(group: Group): Breadcrumb =
    Breadcrumb(group.name.value -> routes.GroupCtrl.detail(group.slug))
}
