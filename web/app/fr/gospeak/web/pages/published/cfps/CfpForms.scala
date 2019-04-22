package fr.gospeak.web.pages.published.cfps

import fr.gospeak.core.domain.{Proposal, Talk}
import fr.gospeak.web.auth.AuthForms
import fr.gospeak.web.auth.AuthForms.{LoginData, SignupData}
import fr.gospeak.web.pages.speaker.talks.TalkForms
import play.api.data.Form
import play.api.data.Forms._

object CfpForms {

  sealed trait TalkAndProposalData {
    val talk: Talk.Data

    def toTalkData: Talk.Data = talk

    def toProposalData: Proposal.Data = Proposal.Data(talk.title, talk.duration, talk.description, talk.slides, talk.video)
  }

  final case class Create(talk: Talk.Data) extends TalkAndProposalData

  val create: Form[Create] = Form(mapping(
    "talk" -> TalkForms.talkMappings
  )(Create.apply)(Create.unapply))

  final case class ProposalSignupData(talk: Talk.Data, user: SignupData) extends TalkAndProposalData

  val signup: Form[ProposalSignupData] = Form(mapping(
    "talk" -> TalkForms.talkMappings,
    "user" -> AuthForms.signupMapping
  )(ProposalSignupData.apply)(ProposalSignupData.unapply))

  final case class ProposalLoginData(talk: Talk.Data, user: LoginData) extends TalkAndProposalData

  val login: Form[ProposalLoginData] = Form(mapping(
    "talk" -> TalkForms.talkMappings,
    "user" -> AuthForms.loginMapping
  )(ProposalLoginData.apply)(ProposalLoginData.unapply))
}
