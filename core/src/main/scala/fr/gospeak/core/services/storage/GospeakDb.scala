package fr.gospeak.core.services.storage

trait GospeakDb {
  val user: UserRepo
  val talk: TalkRepo
  val group: GroupRepo
  val groupSettings: GroupSettingsRepo
  val cfp: CfpRepo
  val partner: PartnerRepo
  val venue: VenueRepo
  val sponsorPack: SponsorPackRepo
  val sponsor: SponsorRepo
  val event: EventRepo
  val proposal: ProposalRepo
  val contact: ContactRepo
  val comment: CommentRepo
  val userRequest: UserRequestRepo
}
