package fr.gospeak.web.api.ui

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.utils.PublicApiError
import fr.gospeak.web.api.utils.JsonFormats._
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl
import fr.gospeak.web.pages.orga.events.routes.EventCtrl
import fr.gospeak.web.pages.orga.partners.routes.PartnerCtrl
import fr.gospeak.web.pages.orga.speakers.routes.SpeakerCtrl
import fr.gospeak.web.utils._
import play.api.libs.json._
import play.api.mvc._

import scala.util.control.NonFatal

case class SuggestedItem(id: String, text: String)

case class SearchResultItem(text: String, url: String)

class SuggestCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  groupRepo: SuggestGroupRepo,
                  userRepo: SuggestUserRepo,
                  cfpRepo: SuggestCfpRepo,
                  eventRepo: SuggestEventRepo,
                  talkRepo: SuggestTalkRepo,
                  proposalRepo: SuggestProposalRepo,
                  partnerRepo: SuggestPartnerRepo,
                  contactRepo: SuggestContactRepo,
                  venueRepo: SuggestVenueRepo,
                  sponsorPackRepo: SuggestSponsorPackRepo,
                  externalCfpRepo: SuggestExternalCfpRepo) extends ApiCtrl(cc, conf) {
  private def SecuredActionIO(block: UserReq[AnyContent] => IO[Result]): Action[AnyContent] = SecuredActionIO(parse.anyContent)(block)

  private def SecuredActionIO[A](bodyParser: BodyParser[A])(block: UserReq[A] => IO[Result]): Action[A] = silhouette.SecuredAction(bodyParser).async { r =>
    block(new UserReq[A](r.request, messagesApi.preferred(r.request), Instant.now(), conf, r, r.identity.user, r.identity.groups))
      .recover { case NonFatal(e) => InternalServerError(Json.toJson(PublicApiError(e.getMessage))) }.unsafeToFuture()
  }


  def suggestTags(): Action[AnyContent] = ActionIO { implicit req =>
    for {
      gTags <- groupRepo.listTags()
      cTags <- cfpRepo.listTags()
      eTags <- eventRepo.listTags()
      tTags <- talkRepo.listTags()
      pTags <- proposalRepo.listTags()
      ecTags <- externalCfpRepo.listTags()
      suggestItems = (gTags ++ cTags ++ eTags ++ tTags ++ pTags ++ ecTags).distinct.map(tag => SuggestedItem(tag.value, tag.value))
    } yield Ok(Json.toJson(suggestItems.sortBy(_.text)))
  }

  def suggestCfps(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSuggest[Cfp](cfpRepo.list, c => SuggestedItem(c.id.value, c.name.value + " - " + Formats.cfpDates(c)))(group)
  }

  def suggestPartners(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSuggest[Partner](partnerRepo.list, p => SuggestedItem(p.id.value, p.name.value))(group)
  }

  def suggestContacts(group: Group.Slug, partner: Partner.Id): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSuggest[Contact](_ => contactRepo.list(partner), p => SuggestedItem(p.id.value, p.name.value))(group)
  }

  def suggestVenues(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSuggest[Venue.Full](venueRepo.listFull, v => SuggestedItem(v.id.value, v.partner.name.value + " - " + v.address.value))(group)
  }

  def suggestSponsorPacks(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSuggest[SponsorPack](sponsorPackRepo.listAll, sp => SuggestedItem(sp.id.value, sp.name.value + " (" + sp.price.value + ")" + (if (sp.active) "" else " (not active)")))(group)
  }

  private def makeSuggest[A](list: Group.Id => IO[Seq[A]], format: A => SuggestedItem)(group: Group.Slug)(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      results <- OptionT.liftF(list(groupElt.id))
      res = Ok(Json.toJson(results.map(format)))
    } yield res).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SuggestedItem]))))
  }

  def searchRoot(group: Group.Slug): Action[AnyContent] = SecuredActionIO { implicit req =>
    IO.pure(BadRequest("this endpoint should not be requested"))
  }

  def searchSpeakers(group: Group.Slug, q: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSearch[User](userRepo.speakers, s => SearchResultItem(s.name.value, SpeakerCtrl.detail(group, s.slug).toString))(group, q)
  }

  def searchProposals(group: Group.Slug, q: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSearch[Proposal.Full](proposalRepo.listFull, p => SearchResultItem(p.title.value, ProposalCtrl.detail(group, p.cfp.slug, p.id).toString))(group, q)
  }

  def searchPartners(group: Group.Slug, q: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSearch[Partner](partnerRepo.list, p => SearchResultItem(p.name.value, PartnerCtrl.detail(group, p.slug).toString))(group, q)
  }

  def searchEvents(group: Group.Slug, q: String): Action[AnyContent] = SecuredActionIO { implicit req =>
    makeSearch[Event](eventRepo.list, e => SearchResultItem(e.name.value, EventCtrl.detail(group, e.slug).toString))(group, q)
  }

  private def makeSearch[A](list: (Group.Id, Page.Params) => IO[Page[A]], format: A => SearchResultItem)(group: Group.Slug, q: String)(implicit req: UserReq[AnyContent]): IO[Result] = {
    (for {
      groupElt <- OptionT(groupRepo.find(req.user.id, group))
      results <- OptionT.liftF(list(groupElt.id, Page.Params.defaults.search(q)))
      res = Ok(Json.toJson(results.items.map(format)))
    } yield res).value.map(_.getOrElse(NotFound(Json.toJson(Seq.empty[SearchResultItem]))))
  }
}
