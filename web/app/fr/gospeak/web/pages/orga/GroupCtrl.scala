package fr.gospeak.web.pages.orga

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Group, UserRequest}
import fr.gospeak.core.services.email.EmailSrv
import fr.gospeak.core.services.storage._
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.pages.orga.settings.SettingsCtrl
import fr.gospeak.web.pages.orga.settings.routes.{SettingsCtrl => SettingsRoutes}
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{OrgaReq, UICtrl, UserReq}
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.Page
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                conf: AppConf,
                userRepo: OrgaUserRepo,
                val groupRepo: OrgaGroupRepo,
                cfpRepo: OrgaCfpRepo,
                eventRepo: OrgaEventRepo,
                venueRepo: OrgaVenueRepo,
                proposalRepo: OrgaProposalRepo,
                sponsorRepo: OrgaSponsorRepo,
                sponsorPackRepo: OrgaSponsorPackRepo,
                partnerRepo: OrgaPartnerRepo,
                userRequestRepo: OrgaUserRequestRepo,
                emailSrv: EmailSrv) extends UICtrl(cc, silhouette, conf) with UICtrl.OrgaAction {
  def create(): Action[AnyContent] = UserAction { implicit req =>
    createForm(GroupForms.create)
  }

  def doCreate(): Action[AnyContent] = UserAction { implicit req =>
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => groupRepo.create(data).map(_ => Redirect(routes.GroupCtrl.detail(data.slug)))
    )
  }

  private def createForm(form: Form[Group.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    val b = groupBreadcrumb.add("New" -> routes.GroupCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def join(params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    for {
      groups <- groupRepo.listJoinable(params)
      pendingRequests <- userRequestRepo.listPendingUserToJoinAGroupRequests
      owners <- userRepo.list(groups.items.flatMap(_.owners.toList).distinct)
      b = groupBreadcrumb.add("Join" -> routes.GroupCtrl.join())
    } yield Ok(html.join(groups, owners, pendingRequests)(b))
  }

  def doJoin(group: Group.Slug, params: Page.Params): Action[AnyContent] = UserAction { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      _ <- OptionT.liftF(userRequestRepo.createUserAskToJoinAGroup(groupElt.id))
    } yield Redirect(UserRoutes.index()).flashing("success" -> s"Join request sent to <b>${groupElt.name.value}</b> group"))
      .value.map(_.getOrElse(Redirect(routes.GroupCtrl.join(params)).flashing("error" -> s"Unable to send join request to <b>$group</b>")))
  }

  def detail(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    for {
      stats <- groupRepo.getStats
      events <- eventRepo.listAfter(Page.Params.defaults.orderBy("start"))
      proposals <- proposalRepo.list(events.items.flatMap(_.talks))
      speakers <- userRepo.list(proposals.flatMap(_.users).distinct)
      sponsors <- sponsorRepo.listAll.map(_.groupBy(_.partner))
      partners <- partnerRepo.list(sponsors.keys.toSeq)
      currentSponsors = sponsors.flatMap { case (id, ss) => partners.find(_.id == id).flatMap(p => ss.find(_.isCurrent(req.now)).map(s => (p, (s, ss.length)))) }.toSeq.sortBy(_._2._1.finish.toEpochDay)
      pastSponsors = sponsors.filter(_._2.forall(!_.isCurrent(req.now))).flatMap { case (id, s) => partners.find(_.id == id).map(p => (p, s)) }.toSeq.sortBy(s => -s._2.map(_.finish.toEpochDay).max)
      packs <- sponsorPackRepo.listAll
      requests <- userRequestRepo.listPendingGroupRequests
      requestUsers <- userRepo.list(requests.flatMap(_.users).distinct)
    } yield Ok(html.detail(stats, events, proposals, speakers, currentSponsors, pastSponsors, packs, requests, requestUsers)(breadcrumb))
  }

  def edit(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    editForm(GroupForms.create)
  }

  def doEdit(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GroupForms.create.bindFromRequest.fold(
      formWithErrors => editForm(formWithErrors),
      data => for {
        newSlugExits <- groupRepo.exists(data.slug)
        res <- if (newSlugExits && data.slug != group) {
          editForm(GroupForms.create.fillAndValidate(data).withError("slug", s"Slug ${data.slug.value} already taken by an other group"))
        } else {
          groupRepo.edit(data).map(_ => Redirect(SettingsRoutes.settings(data.slug)))
        }
      } yield res
    )
  }

  private def editForm(form: Form[Group.Data])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val filledForm = if (form.hasErrors) form else form.fill(req.group.data)
    val b = SettingsCtrl.listBreadcrumb.add("Edit group" -> routes.GroupCtrl.edit(req.group.slug))
    IO.pure(Ok(html.edit(filledForm)(b)))
  }

  def acceptJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.acceptUserToJoinAGroup(requestElt))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupAccepted(userElt)))
      next = redirectToPreviousPageOr(routes.GroupCtrl.detail(group))
      msg = s"You accepted <b>${userElt.name.value}</b> as organizer of ${req.group.name.value}"
    } yield next.flashing("success" -> msg)).value.map(_.getOrElse(groupNotFound(group)))
  }

  def rejectJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.rejectUserToJoinAGroup(requestElt))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupRejected(userElt)))
      next = redirectToPreviousPageOr(routes.GroupCtrl.detail(group))
      msg = s"You refused to <b>${userElt.name.value}</b> to join ${req.group.name.value} as an organizer"
    } yield next.flashing("error" -> msg)).value.map(_.getOrElse(groupNotFound(group)))
  }

  def contactMembers(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    contactMembersView(GroupForms.contactMembers)
  }

  def doContactMembers(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    GroupForms.contactMembers.bindFromRequest.fold(
      formWithErrors => contactMembersView(formWithErrors),
      data => (for {
        sender <- OptionT(IO.pure(req.group.senders(req.user).find(_.address == data.from)))
        members <- OptionT.liftF(groupRepo.listMembers)
        _ <- OptionT.liftF(members.map(m => emailSrv.send(Emails.groupMessage(req.group, sender, data.subject, data.content, m))).sequence)
        next = Redirect(routes.GroupCtrl.detail(group)).flashing("success" -> "Message sent to group members")
      } yield next).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def contactMembersView(form: Form[GroupForms.ContactMembers])(implicit req: OrgaReq[AnyContent]): IO[Result] = {
    val b = breadcrumb.add("Contact members" -> routes.GroupCtrl.contactMembers(req.group.slug))
    IO.pure(Ok(html.contactMembers(form)(b)))
  }
}

object GroupCtrl {
  def groupBreadcrumb(implicit req: UserReq[AnyContent]): Breadcrumb =
    UserCtrl.breadcrumb.add("Groups" -> UserRoutes.index())

  def breadcrumb(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    Breadcrumb(req.group.name.value -> routes.GroupCtrl.detail(req.group.slug))
}
