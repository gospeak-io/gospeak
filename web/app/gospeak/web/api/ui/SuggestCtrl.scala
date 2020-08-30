package gospeak.web.api.ui

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.core.domain._
import gospeak.core.services.storage._
import gospeak.libs.scala.domain.Page
import gospeak.web.AppConf
import gospeak.web.api.domain.utils.ApiResult
import gospeak.web.api.ui.helpers.JsonFormats._
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.pages.orga.cfps.proposals.routes.ProposalCtrl
import gospeak.web.pages.orga.events.routes.EventCtrl
import gospeak.web.pages.orga.partners.routes.PartnerCtrl
import gospeak.web.pages.orga.speakers.routes.SpeakerCtrl
import gospeak.web.utils._
import play.api.mvc._

case class SuggestedItem(id: String, text: String)

case class SearchResultItem(text: String, url: String)

class SuggestCtrl(cc: ControllerComponents,
                  silhouette: Silhouette[CookieEnv],
                  conf: AppConf,
                  val groupRepo: SuggestGroupRepo with OrgaGroupRepo,
                  userRepo: SuggestUserRepo,
                  cfpRepo: SuggestCfpRepo,
                  eventRepo: SuggestEventRepo,
                  talkRepo: SuggestTalkRepo,
                  proposalRepo: SuggestProposalRepo,
                  partnerRepo: SuggestPartnerRepo,
                  contactRepo: SuggestContactRepo,
                  venueRepo: SuggestVenueRepo,
                  sponsorPackRepo: SuggestSponsorPackRepo,
                  externalEventRepo: SuggestExternalEventRepo,
                  externalProposalRepo: SuggestExternalProposalRepo) extends ApiCtrl(cc, silhouette, conf) with ApiCtrl.OrgaAction {
  def suggestTags(): Action[AnyContent] = UserAwareAction { implicit req =>
    for {
      gTags <- groupRepo.listTags()
      cTags <- cfpRepo.listTags()
      eTags <- eventRepo.listTags()
      tTags <- talkRepo.listTags()
      pTags <- proposalRepo.listTags()
      eeTags <- externalEventRepo.listTags()
      epTags <- externalProposalRepo.listTags()
      suggestItems = (gTags ++ cTags ++ eTags ++ tTags ++ pTags ++ eeTags ++ epTags).distinct.map(tag => SuggestedItem(tag.value, tag.value))
    } yield ApiResult.of(suggestItems.sortBy(_.text))
  }

  def suggestOrgaTags(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    for {
      pTags <- proposalRepo.listOrgaTags()
      suggestItems = pTags.distinct.map(tag => SuggestedItem(tag.value, tag.value))
    } yield ApiResult.of(suggestItems.sortBy(_.text))
  }

  def suggestCfps(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    makeSuggest[Cfp](cfpRepo.list, c => SuggestedItem(c.id.value, c.name.value + " - " + c.asDates))(group)
  }

  def suggestPartners(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    makeSuggest[Partner](partnerRepo.list, p => SuggestedItem(p.id.value, p.name.value))(group)
  }

  def suggestContacts(group: Group.Slug, partner: Partner.Id): Action[AnyContent] = OrgaAction(group) { implicit req =>
    makeSuggest[Contact](_ => contactRepo.list(partner), p => SuggestedItem(p.id.value, p.name.value))(group)
  }

  def suggestVenues(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    makeSuggest[Venue.Full](venueRepo.listFull, v => SuggestedItem(v.id.value, s"${v.partner.name.value} - ${v.address.value}"))(group)
  }

  def suggestSponsorPacks(group: Group.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    makeSuggest[SponsorPack](sponsorPackRepo.listAll, sp => SuggestedItem(sp.id.value, s"${sp.name.value} (${sp.price.value})${if (sp.active) "" else " (not active)"}"))(group)
  }

  // TODO: use OrgaReq instead of Group.Id to list items
  private def makeSuggest[A](list: Group.Id => IO[List[A]], format: A => SuggestedItem)
                            (group: Group.Slug)
                            (implicit req: OrgaReq[AnyContent]): IO[ApiResult[List[SuggestedItem]]] = {
    list(req.group.id).map(results => ApiResult.of(results.map(format)))
  }

  def logosExternalEvents(): Action[AnyContent] = UserAwareAction { implicit req =>
    externalEventRepo.listLogos().map(logos => ApiResult.of(logos.map(_.value).sorted))
  }

  def searchRoot(group: Group.Slug): Action[AnyContent] = OrgaAction[List[SearchResultItem]](group) { implicit req =>
    IO.pure(ApiResult.forbidden("this endpoint should not be requested"))
  }

  def searchSpeakers(group: Group.Slug, q: String): Action[AnyContent] = OrgaAction[List[SearchResultItem]](group) { implicit req =>
    makeSearch[User.Full](userRepo.speakers, s => SearchResultItem(s.name.value, SpeakerCtrl.detail(group, s.slug).toString))(group, q)
  }

  def searchProposals(group: Group.Slug, q: String): Action[AnyContent] = OrgaAction[List[SearchResultItem]](group) { implicit req =>
    makeSearch[Proposal.Full](proposalRepo.listFull, p => SearchResultItem(p.title.value, ProposalCtrl.detail(group, p.cfp.slug, p.id).toString))(group, q)
  }

  def searchPartners(group: Group.Slug, q: String): Action[AnyContent] = OrgaAction[List[SearchResultItem]](group) { implicit req =>
    makeSearch[Partner](partnerRepo.list, p => SearchResultItem(p.name.value, PartnerCtrl.detail(group, p.slug).toString))(group, q)
  }

  def searchEvents(group: Group.Slug, q: String): Action[AnyContent] = OrgaAction[List[SearchResultItem]](group) { implicit req =>
    makeSearch[Event](eventRepo.list, e => SearchResultItem(e.name.value, EventCtrl.detail(group, e.slug).toString))(group, q)
  }

  private def makeSearch[A](list: Page.Params => IO[Page[A]], format: A => SearchResultItem)
                           (group: Group.Slug, q: String)
                           (implicit req: OrgaReq[AnyContent]): IO[ApiResult[List[SearchResultItem]]] = {
    list(Page.Params.defaults.search(q)).map(results => ApiResult.of(results.items.map(format)))
  }
}
