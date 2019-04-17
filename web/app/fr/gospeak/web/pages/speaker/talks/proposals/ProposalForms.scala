package fr.gospeak.web.pages.speaker.talks.proposals

import fr.gospeak.core.domain.Proposal
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms.{mapping, optional}

object ProposalForms {
  val create: Form[Proposal.Data] = Form(mapping(
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video)
  )(Proposal.Data.apply)(Proposal.Data.unapply))
}
