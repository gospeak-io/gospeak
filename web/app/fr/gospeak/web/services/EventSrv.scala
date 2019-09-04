package fr.gospeak.web.services

import java.time.LocalDateTime

import cats.data.OptionT
import cats.effect.IO
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage._
import fr.gospeak.infra.services.TemplateSrv
import fr.gospeak.libs.scalautils.domain.Markdown
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.MessageBuilder
import fr.gospeak.web.services.EventSrv.EventFull
import play.api.mvc.AnyContent

class EventSrv(groupRepo: OrgaGroupRepo,
               cfpRepo: OrgaCfpRepo,
               eventRepo: OrgaEventRepo,
               venueRepo: OrgaVenueRepo,
               proposalRepo: OrgaProposalRepo,
               userRepo: OrgaUserRepo,
               builder: MessageBuilder,
               templateSrv: TemplateSrv) {
  def getFullEvent(group: Group.Slug, event: Event.Slug, user: User.Id): IO[Option[EventFull]] = {
    (for {
      groupElt <- OptionT(groupRepo.find(user, group))
      eventElt <- OptionT(eventRepo.find(groupElt.id, event))
      cfpOpt <- OptionT.liftF(cfpRepo.find(eventElt.id))
      venueOpt <- OptionT.liftF(venueRepo.list(groupElt.id, eventElt.venue.toList).map(_.headOption))
      talks <- OptionT.liftF(proposalRepo.list(eventElt.talks)
        .map(proposals => eventElt.talks.flatMap(t => proposals.find(_.id == t)))) // to keep talk order
      speakers <- OptionT.liftF(userRepo.list(talks.flatMap(_.users).distinct))
    } yield EventFull(groupElt, eventElt, cfpOpt, venueOpt, talks, speakers)).value
  }

  def buildDescription(event: EventFull, nowLDT: LocalDateTime)(implicit req: SecuredRequest[CookieEnv, AnyContent]): Markdown = {
    val data = builder.buildEventInfo(event, nowLDT)
    templateSrv.render(event.event.description, data).getOrElse(Markdown(event.event.description.value))
  }
}

object EventSrv {

  final case class EventFull(group: Group,
                             event: Event,
                             cfpOpt: Option[Cfp],
                             venueOpt: Option[(Partner, Venue)],
                             talks: Seq[Proposal],
                             speakers: Seq[User])

}
