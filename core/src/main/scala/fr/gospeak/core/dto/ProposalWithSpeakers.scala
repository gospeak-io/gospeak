package fr.gospeak.core.dto

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNec}
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{Markdown, Slides, Tag, Video}

import scala.concurrent.duration.FiniteDuration

final case class ProposalWithSpeakers(id: Proposal.Id,
                                      talk: Talk.Id,
                                      cfp: Cfp.Id,
                                      event: Option[Event.Id],
                                      status: Proposal.Status,
                                      title: Talk.Title,
                                      duration: FiniteDuration,
                                      description: Markdown,
                                      speakers: NonEmptyList[User],
                                      slides: Option[Slides],
                                      video: Option[Video],
                                      tags: Seq[Tag],
                                      info: Info)

object ProposalWithSpeakers {
  def from(proposal: Proposal, speakers: Seq[User]): ValidatedNec[String, ProposalWithSpeakers] = {
    validSpeakers(proposal, speakers).map { s =>
      new ProposalWithSpeakers(
        id = proposal.id,
        talk = proposal.talk,
        cfp = proposal.cfp,
        event = proposal.event,
        status = proposal.status,
        title = proposal.title,
        duration = proposal.duration,
        description = proposal.description,
        speakers = s,
        slides = proposal.slides,
        video = proposal.video,
        tags = proposal.tags,
        info = proposal.info)
    }
  }

  private def validSpeakers(proposal: Proposal, speakers: Seq[User]): ValidatedNec[String, NonEmptyList[User]] = {
    val head = proposal.speakers.head
    val tail = proposal.speakers.tail
    tail.foldLeft(validSpeaker(head, speakers)) { (acc, id) =>
      (acc, validSpeaker(id, speakers)) match {
        case (Invalid(accErr), Invalid(err)) => Validated.invalid(accErr ++ err)
        case (Invalid(accErr), _) => Validated.invalid(accErr)
        case (_, Invalid(err)) => Validated.invalid(err)
        case (Valid(res), Valid(elt)) => Validated.Valid(res ::: elt)
      }
    }
  }

  private def validSpeaker(id: User.Id, speakers: Seq[User]): ValidatedNec[String, NonEmptyList[User]] =
    speakers.find(_.id == id).map(u => Validated.Valid(NonEmptyList.of(u))).getOrElse(Validated.invalidNec(s"User not found but expect ${id.value}"))
}


