package gospeak.web.pages.orga.cfps.proposals

import gospeak.core.domain.Proposal
import gospeak.web.utils.Mappings.{slides, video}
import play.api.data.Form
import play.api.data.Forms.{mapping, optional}
import gospeak.web.utils.Mappings._

object ProposalForms {
  val create: Form[Proposal.DataOrga] = Form(mapping(
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags,
    "orgaTags" -> tags
  )(Proposal.DataOrga.apply)(Proposal.DataOrga.unapply))
}
