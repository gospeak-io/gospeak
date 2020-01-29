package gospeak.web.pages.user.talks.proposals

import gospeak.core.domain.Proposal
import gospeak.web.utils.Mappings._
import gospeak.libs.scala.domain.EmailAddress
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, single}

object ProposalForms {
  val create: Form[Proposal.Data] = Form(mapping(
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(Proposal.Data.apply)(Proposal.Data.unapply))

  val addSpeaker: Form[EmailAddress] = Form(single(
    "email" -> emailAddress
  ))
}
