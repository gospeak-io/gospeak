package fr.gospeak.web.pages.orga.partners.contacts

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.ApplicationConf
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Contact, Group, Partner}
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.exceptions.DuplicateEmailException
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.orga.partners.PartnerCtrl
import fr.gospeak.web.pages.orga.partners.contacts.ContactCtrl._
import fr.gospeak.web.pages.orga.partners.routes.{PartnerCtrl => PartnerRoutes}
import fr.gospeak.web.utils.Mappings._
import fr.gospeak.web.utils.{OrgaReq, UICtrl}
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}

import scala.util.control.NonFatal

class ContactCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  env: ApplicationConf.Env,
                  contactRepo: ContactRepo,
                  userRepo: OrgaUserRepo,
                  val groupRepo: OrgaGroupRepo,
                  partnerRepo: OrgaPartnerRepo,
                  venueRepo: OrgaVenueRepo,
                  sponsorRepo: OrgaSponsorRepo) extends UICtrl(cc, silhouette, env) with UICtrl.OrgaAction {
  private val createForm: Form[Contact.Data] = Form(mapping(
    "partner" -> partnerId,
    "first_name" -> contactFirstName,
    "last_name" -> contactLastName,
    "email" -> emailAddress,
    "description" -> markdown
  )(Contact.Data.apply)(Contact.Data.unapply))

  def list(group: Group.Slug, partner: Partner.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      contacts <- OptionT.liftF(contactRepo.list(partnerElt.id, params))
      b = listBreadcrumb(partnerElt)
    } yield Ok(html.list(partnerElt, contacts)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  })

  def create(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createView(group, partner, createForm)
  })

  def doCreate(group: Group.Slug, partner: Partner.Slug): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createForm.bindFromRequest.fold(
      formWithErrors => createView(group, partner, formWithErrors),
      data => (for {
        partnerElt <- OptionT(partnerRepo.find(partner))
        exists <- OptionT.liftF(contactRepo.exists(partnerElt.id, data.email))
        _ <- OptionT.liftF((!exists).toIO(DuplicateEmailException(data.email)))
        contact <- OptionT.liftF(contactRepo.create(data))
      } yield Redirect(routes.ContactCtrl.detail(group, partner, contact.id))).value.map(_.getOrElse(partnerNotFound(group, partner)))
        .recoverWith {
          case _: DuplicateEmailException => createView(group, partner, createForm.bindFromRequest().withError("email", "Email already exists"))
          case NonFatal(e) => createView(group, partner, createForm.bindFromRequest().withGlobalError(e.getMessage))
        }
    )
  })

  private def createView(group: Group.Slug, partner: Partner.Slug, form: Form[Contact.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      b = listBreadcrumb(partnerElt).add("New" -> routes.ContactCtrl.create(group, partner))
      call = routes.ContactCtrl.doCreate(group, partner)
    } yield Ok(html.create(partnerElt, form, call)(b))).value.map(_.getOrElse(partnerNotFound(group, partner)))
  }

  def detail(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      contactElt <- OptionT(contactRepo.find(contact))
      contactVenues <- OptionT.liftF(venueRepo.listAll(contactElt.id))
      contactSponsors <- OptionT.liftF(sponsorRepo.listAll(contactElt.id))
      b = breadcrumb(partnerElt, contactElt)
      res = Ok(html.detail(partnerElt, contactElt, contactVenues, contactSponsors)(b))
    } yield res).value.map(_.getOrElse(contactNotFound(group, partner, contact)))
  })

  def edit(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    editView(group, partner, contact, createForm)
  })

  def doEdit(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    createForm.bindFromRequest.fold(
      formWithErrors => editView(group, partner, contact, formWithErrors),
      data => (for {
        partnerElt <- OptionT(partnerRepo.find(partner))
        exists <- OptionT.liftF(contactRepo.exists(partnerElt.id, data.email))
        _ <- OptionT.liftF((!exists).toIO(DuplicateEmailException(data.email)))
        _ <- OptionT.liftF(contactRepo.edit(contact, data))
      } yield Redirect(routes.ContactCtrl.detail(group, partner, contact))).value.map(_.getOrElse(partnerNotFound(group, partner)))
        .recoverWith {
          case _: DuplicateEmailException => createView(group, partner, createForm.bindFromRequest().withError("email", "Email already exists"))
          case NonFatal(e) => createView(group, partner, createForm.bindFromRequest().withGlobalError(e.getMessage))
        }
    )
  })

  private def editView(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id, form: Form[Contact.Data])(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Result] = {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      contactElt <- OptionT(contactRepo.find(contact))
      b = breadcrumb(partnerElt, contactElt).add("Edit" -> routes.ContactCtrl.edit(group, partner, contact))
      filledForm = if (form.hasErrors) form else form.fill(contactElt.data)
    } yield Ok(html.edit(partnerElt, contactElt, filledForm)(b))).value.map(_.getOrElse(contactNotFound(group, partner, contact)))
  }

  def doRemove(group: Group.Slug, partner: Partner.Slug, contact: Contact.Id): Action[AnyContent] = OrgaAction(group)(implicit req => implicit ctx => {
    (for {
      partnerElt <- OptionT(partnerRepo.find(partner))
      _ <- OptionT.liftF(contactRepo.remove(partnerElt.id, contact))
    } yield Redirect(PartnerRoutes.detail(group, partner))).value.map(_.getOrElse(contactNotFound(group, partner, contact)))
  })
}

object ContactCtrl {
  def listBreadcrumb(partner: Partner)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    PartnerCtrl.breadcrumb(partner).add("Contacts" -> routes.ContactCtrl.list(req.group.slug, partner.slug))

  def breadcrumb(partner: Partner, contact: Contact)(implicit req: OrgaReq[AnyContent]): Breadcrumb =
    listBreadcrumb(partner).add(contact.email.value -> routes.ContactCtrl.detail(req.group.slug, partner.slug, contact.id))
}
