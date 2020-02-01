package gospeak.core.services.storage

trait GsRepo {
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
  val externalEvent: ExternalEventRepo
  val externalCfp: ExternalCfpRepo
  val externalProposal: ExternalProposalRepo
}
