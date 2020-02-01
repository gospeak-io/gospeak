package gospeak.web.pages.user.talks

import gospeak.core.domain.{ExternalProposal, Talk}
import gospeak.web.utils.Mappings._
import play.api.data.Forms._
import play.api.data.{Form, Mapping}

object TalkForms {
  val talkMappings: Mapping[Talk.Data] = mapping(
    "slug" -> talkSlug,
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(Talk.Data.apply)(Talk.Data.unapply)
  val create: Form[Talk.Data] = Form(talkMappings)

  val externalProposalMappings: Mapping[ExternalProposal.Data] = mapping(
    "title" -> externalProposalTitle,
    "duration" -> duration,
    "description" -> markdown,
    "slides" -> optional(slides),
    "video" -> optional(video),
    "tags" -> tags
  )(ExternalProposal.Data.apply)(ExternalProposal.Data.unapply)
  val createExternalProposal: Form[ExternalProposal.Data] = Form(externalProposalMappings)
}
