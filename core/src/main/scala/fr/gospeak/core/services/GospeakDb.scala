package fr.gospeak.core.services

trait GospeakDb {
  val user: UserRepo
  val userRequest: UserRequestRepo
  val group: GroupRepo
  val event: EventRepo
  val cfp: CfpRepo
  val talk: TalkRepo
  val proposal: ProposalRepo
}
