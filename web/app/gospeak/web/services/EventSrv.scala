package gospeak.web.services

import cats.data.OptionT
import cats.effect.IO
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.TemplateSrv
import gospeak.core.services.storage._
import gospeak.web.domain.MessageBuilder
import gospeak.web.services.EventSrv.EventFull
import gospeak.web.utils.{OrgaReq, UserReq}
import gospeak.libs.scala.domain.Markdown
import play.api.mvc.AnyContent

class EventSrv(groupRepo: OrgaGroupRepo,
               cfpRepo: OrgaCfpRepo,
               eventRepo: OrgaEventRepo,
               venueRepo: OrgaVenueRepo,
               proposalRepo: OrgaProposalRepo,
               userRepo: OrgaUserRepo,
               builder: MessageBuilder,
               templateSrv: TemplateSrv) {
  def getFullEvent(event: Event.Slug)(implicit req: OrgaReq[AnyContent], ctx: OrgaCtx): IO[Option[EventFull]] = {
    (for {
      eventElt <- OptionT(eventRepo.find(event))
      cfpOpt <- OptionT.liftF(cfpRepo.find(eventElt.id))
      venueOpt <- OptionT.liftF(venueRepo.listAllFull(eventElt.venue.toList).map(_.headOption))
      talks <- OptionT.liftF(proposalRepo.list(eventElt.talks)
        .map(proposals => eventElt.talks.flatMap(t => proposals.find(_.id == t)))) // to keep talk order
      speakers <- OptionT.liftF(userRepo.list(talks.flatMap(_.users).distinct))
    } yield EventFull(req.group, eventElt, cfpOpt, venueOpt, talks, speakers)).value
  }

  def buildDescription(event: EventFull)(implicit req: UserReq[AnyContent]): Markdown = {
    val data = builder.buildEventInfo(event)
    templateSrv.render(event.event.description, data).getOrElse(Markdown(event.event.description.value))
  }
}

object EventSrv {

  final case class EventFull(group: Group,
                             event: Event,
                             cfpOpt: Option[Cfp],
                             venueOpt: Option[Venue.Full],
                             talks: Seq[Proposal],
                             speakers: Seq[User])

}
