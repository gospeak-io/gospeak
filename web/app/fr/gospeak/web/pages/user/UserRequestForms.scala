package fr.gospeak.web.pages.user

import fr.gospeak.core.domain.UserRequest.ProposalCreation
import fr.gospeak.web.pages.orga.events.EventForms
import play.api.data.Form

object UserRequestForms {
  val loggedProposalInvite: Form[ProposalCreation.Submission] = Form(EventForms.submissionMapping)
}
