package fr.gospeak.web.pages.published.cfps

import java.time.LocalDateTime

import gospeak.core.domain.{ExternalCfp, Proposal, Talk}
import fr.gospeak.web.auth.AuthForms
import fr.gospeak.web.auth.AuthForms.{LoginData, SignupData}
import fr.gospeak.web.pages.user.talks.TalkForms
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object CfpForms {

  sealed trait TalkAndProposalData {
    val talk: Talk.Data

    def toTalkData: Talk.Data = talk

    def toProposalData: Proposal.Data = Proposal.Data(talk)
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

  val external: Form[ExternalCfp.Data] = Form(mapping(
    "name" -> externalCfpName,
    "logo" -> optional(logo),
    "description" -> markdown,
    "begin" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(0, 0), _.toLocalDate)),
    "close" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(23, 59), _.toLocalDate)),
    "url" -> url,
    "event" -> mapping(
      "start" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(0, 0), _.toLocalDate)),
      "finish" -> optional(localDate(localDateFormat).transform[LocalDateTime](_.atTime(23, 59), _.toLocalDate)),
      "url" -> optional(url),
      "address" -> optional(gMapPlace),
      "tickets" -> optional(url),
      "videos" -> optional(url),
      "twitterAccount" -> optional(twitterAccount),
      "twitterHashtag" -> optional(twitterHashtag)
    )(ExternalCfp.Event.apply)(ExternalCfp.Event.unapply),
    "tags" -> tags
  )(ExternalCfp.Data.apply)(ExternalCfp.Data.unapply))
}
