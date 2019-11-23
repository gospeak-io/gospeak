package fr.gospeak.web.pages.orga

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.{Group, User, UserRequest}
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.infra.services.EmailSrv
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain._
import fr.gospeak.web.emails.Emails
import fr.gospeak.web.pages.orga.GroupCtrl._
import fr.gospeak.web.pages.orga.settings.SettingsCtrl
import fr.gospeak.web.pages.orga.settings.routes.{SettingsCtrl => SettingsRoutes}
import fr.gospeak.web.pages.user.UserCtrl
import fr.gospeak.web.pages.user.routes.{UserCtrl => UserRoutes}
import fr.gospeak.web.utils.{HttpUtils, UICtrl, UserReq}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class GroupCtrl(cc: ControllerComponents,
                silhouette: Silhouette[CookieEnv],
                env: ApplicationConf.Env,
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
                emailSrv: EmailSrv,
                timeShape: TimeShape) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  def create(): Action[AnyContent] = SecuredActionIO { implicit req =>
    createForm(GroupForms.create(timeShape))
  }

  def doCreate(): Action[AnyContent] = SecuredActionIO { implicit req =>
    GroupForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => createForm(formWithErrors),
      data => for {
        // TODO check if slug not already exist
        _ <- groupRepo.create(data, req.user.id, req.now)
      } yield Redirect(routes.GroupCtrl.detail(data.slug))
    )
  }

  private def createForm(form: Form[Group.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    val b = groupBreadcrumb(req.user).add("New" -> routes.GroupCtrl.create())
    IO.pure(Ok(html.create(form)(b)))
  }

  def join(params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    for {
      groups <- groupRepo.listJoinable(req.user.id, params)
      pendingRequests <- userRequestRepo.listPendingUserToJoinAGroupRequests(req.user.id)
      owners <- userRepo.list(groups.items.flatMap(_.owners.toList).distinct)
      b = groupBreadcrumb(req.user).add("Join" -> routes.GroupCtrl.join())
    } yield Ok(html.join(groups, owners, pendingRequests)(b))
  }

  def doJoin(group: Group.Slug, params: Page.Params): Action[AnyContent] = SecuredActionIO { implicit req =>
    (for {
      groupElt <- OptionT(groupRepo.find(group))
      _ <- OptionT.liftF(userRequestRepo.createUserAskToJoinAGroup(req.user.id, groupElt.id, req.now))
    } yield Redirect(UserRoutes.index()).flashing("success" -> s"Join request sent to <b>${groupElt.name.value}</b> group"))
      .value.map(_.getOrElse(Redirect(routes.GroupCtrl.join(params)).flashing("error" -> s"Unable to send join request to <b>$group</b>")))
  }

  def detail(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      events <- OptionT.liftF(eventRepo.listAfter(req.group.id, req.now, Page.Params.defaults.orderBy("start")))
      cfps <- OptionT.liftF(cfpRepo.list(events.items.flatMap(_.cfp)))
      venues <- OptionT.liftF(venueRepo.listFull(events.items.flatMap(_.venue)))
      proposals <- OptionT.liftF(proposalRepo.list(events.items.flatMap(_.talks)))
      speakers <- OptionT.liftF(userRepo.list(proposals.flatMap(_.users).distinct))
      sponsors <- OptionT.liftF(sponsorRepo.listAll(req.group.id).map(_.groupBy(_.partner)))
      partners <- OptionT.liftF(partnerRepo.list(sponsors.keys.toSeq))
      currentSponsors = sponsors.flatMap { case (id, ss) => partners.find(_.id == id).flatMap(p => ss.find(_.isCurrent(req.now)).map(s => (p, (s, ss.length)))) }.toSeq.sortBy(_._2._1.finish.toEpochDay)
      pastSponsors = sponsors.filter(_._2.forall(!_.isCurrent(req.now))).flatMap { case (id, s) => partners.find(_.id == id).map(p => (p, s)) }.toSeq.sortBy(s => -s._2.map(_.finish.toEpochDay).max)
      packs <- OptionT.liftF(sponsorPackRepo.listAll(req.group.id))
      requests <- OptionT.liftF(userRequestRepo.listPendingGroupRequests(req.group.id, req.now))
      requestUsers <- OptionT.liftF(userRepo.list(requests.flatMap(_.users).distinct))
      b = breadcrumb(req.group)
      res = Ok(html.detail(req.group, events, cfps, venues, proposals, speakers, currentSponsors, pastSponsors, packs, requests, requestUsers)(b))
    } yield res).value.map(_.getOrElse(groupNotFound(group)))
  })

  def edit(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    editForm(group, GroupForms.create(timeShape))
  }

  def doEdit(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    GroupForms.create(timeShape).bindFromRequest.fold(
      formWithErrors => editForm(group, formWithErrors),
      data => (for {
        _ <- OptionT(groupRepo.find(req.user.id, group)) // to check that user is a group owner
        newSlugExits <- OptionT.liftF(groupRepo.exists(data.slug))
        res <- OptionT.liftF(
          if (newSlugExits && data.slug != group) {
            editForm(group, GroupForms.create(timeShape).fillAndValidate(data).withError("slug", s"Slug ${data.slug.value} already taken by an other group"))
          } else {
            groupRepo.edit(group)(data, req.user.id, req.now).map { _ => Redirect(SettingsRoutes.settings(data.slug)) }
          })
      } yield res).value.map(_.getOrElse(groupNotFound(group)))
    )
  }

  private def editForm(group: Group.Slug, form: Form[Group.Data])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      b = SettingsCtrl.listBreadcrumb(groupElt).add("Edit group" -> routes.GroupCtrl.edit(group))
      filledForm = if (form.hasErrors) form else form.fill(groupElt.data)
    } yield Ok(html.edit(groupElt, filledForm)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }

  def acceptJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.GroupCtrl.detail(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(groupElt.id, userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.acceptUserToJoinAGroup(requestElt, req.user.id, req.now))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupAccepted(userElt, groupElt)))
      msg = s"You accepted <b>${userElt.name.value}</b> as organizer of ${groupElt.name.value}"
    } yield next.flashing("success" -> msg)).value.map(_.getOrElse(groupNotFound(group)))
  }

  def rejectJoin(group: Group.Slug, userRequest: UserRequest.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    val next = Redirect(HttpUtils.getReferer(req).getOrElse(routes.GroupCtrl.detail(group).toString))
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      requestElt <- OptionT(userRequestRepo.findPendingUserToJoinAGroup(groupElt.id, userRequest))
      userElt <- OptionT(userRepo.find(requestElt.createdBy))
      _ <- OptionT.liftF(userRequestRepo.rejectUserToJoinAGroup(requestElt, req.user.id, req.now))
      _ <- OptionT.liftF(emailSrv.send(Emails.joinGroupRejected(userElt, groupElt)))
      msg = s"You refused to <b>${userElt.name.value}</b> to join ${groupElt.name.value} as an organizer"
    } yield next.flashing("error" -> msg)).value.map(_.getOrElse(groupNotFound(group)))
  }

  def contactMembers(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    contactMembersView(group, GroupForms.contactMembers)
  }

  def doContactMembers(group: Group.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    GroupForms.contactMembers.bindFromRequest.fold(
      formWithErrors => contactMembersView(group, formWithErrors),
      data => (for {
        groupElt <- OptionT(groupRepo.find(req.user.id, group))
        sender <- OptionT(IO.pure(groupElt.senders(req.user).find(_.address == data.from)))
        members <- OptionT.liftF(groupRepo.listMembers)
        _ <- OptionT.liftF(members.map(m => emailSrv.send(Emails.groupMessage(groupElt, sender, data.subject, data.content, m))).sequence)
        next = Redirect(routes.GroupCtrl.detail(group))
      } yield next.flashing("success" -> "Message sent to group members")).value.map(_.getOrElse(groupNotFound(group)))
    ).recover {
      case NonFatal(e) => Redirect(routes.GroupCtrl.detail(group)).flashing("error" -> s"An error happened: ${e.getMessage}")
    }
  })

  private def contactMembersView(group: Group.Slug, form: Form[GroupForms.ContactMembers])(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      senders = groupElt.senders(req.user)
      b = breadcrumb(groupElt).add("Contact members" -> routes.GroupCtrl.contactMembers(group))
    } yield Ok(html.contactMembers(groupElt, senders, form)(b))).value.map(_.getOrElse(groupNotFound(group)))
  }
}

object GroupCtrl {
  def groupBreadcrumb(user: User): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Groups" -> UserRoutes.index())

  def breadcrumb(group: Group): Breadcrumb =
    Breadcrumb(group.name.value -> routes.GroupCtrl.detail(group.slug))
}
