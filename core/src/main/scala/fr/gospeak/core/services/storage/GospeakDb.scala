package fr.gospeak.core.services.storage

trait GospeakDb {
  val user: UserRepo
  val userRequest: UserRequestRepo
  val talk: TalkRepo
  val group: GroupRepo
  val cfp: CfpRepo
  val partner: PartnerRepo
  val venue: VenueRepo
  val event: EventRepo
  val proposal: ProposalRepo
  val settings: SettingsRepo
}
