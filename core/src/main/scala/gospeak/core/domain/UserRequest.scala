package gospeak.core.domain

import java.time.Instant

import gospeak.libs.scala.domain.{DataClass, EmailAddress, IId, UuidIdBuilder}

import scala.concurrent.duration._

sealed trait UserRequest {
  val id: UserRequest.Id
  val createdAt: Instant

  def users: List[User.Id] = List()

  def isPending(now: Instant): Boolean
}

object UserRequest {

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("UserRequest.Id", new Id(_))

  sealed trait StdUserRequest extends UserRequest {
    val createdBy: User.Id
    val accepted: Option[Meta]
    val rejected: Option[Meta]
    val canceled: Option[Meta]

    override def users: List[User.Id] = List(createdBy) ++ accepted.map(_.by).toList ++ rejected.map(_.by).toList ++ canceled.map(_.by).toList

    override def isPending(now: Instant): Boolean = accepted.isEmpty && rejected.isEmpty && canceled.isEmpty
  }

  final case class AccountValidationRequest(id: Id,
                                            email: EmailAddress,
                                            deadline: Instant,
                                            createdAt: Instant,
                                            createdBy: User.Id,
                                            acceptedAt: Option[Instant]) extends UserRequest {
    override def users: List[User.Id] = List(createdBy)

    override def isPending(now: Instant): Boolean = deadline.isAfter(now) && acceptedAt.isEmpty
  }

  final case class PasswordResetRequest(id: Id,
                                        email: EmailAddress,
                                        deadline: Instant,
                                        createdAt: Instant,
                                        acceptedAt: Option[Instant]) extends UserRequest {
    override def isPending(now: Instant): Boolean = deadline.isAfter(now) && acceptedAt.isEmpty
  }

  final case class UserAskToJoinAGroupRequest(id: Id,
                                              group: Group.Id,
                                              createdAt: Instant,
                                              createdBy: User.Id,
                                              accepted: Option[Meta],
                                              rejected: Option[Meta],
                                              canceled: Option[Meta]) extends StdUserRequest

  final case class GroupInvite(id: Id,
                               group: Group.Id,
                               email: EmailAddress,
                               createdAt: Instant,
                               createdBy: User.Id,
                               accepted: Option[Meta],
                               rejected: Option[Meta],
                               canceled: Option[Meta]) extends StdUserRequest

  final case class TalkInvite(id: Id,
                              talk: Talk.Id,
                              email: EmailAddress,
                              createdAt: Instant,
                              createdBy: User.Id,
                              accepted: Option[Meta],
                              rejected: Option[Meta],
                              canceled: Option[Meta]) extends StdUserRequest

  final case class ProposalInvite(id: Id,
                                  proposal: Proposal.Id,
                                  email: EmailAddress,
                                  createdAt: Instant,
                                  createdBy: User.Id,
                                  accepted: Option[Meta],
                                  rejected: Option[Meta],
                                  canceled: Option[Meta]) extends StdUserRequest

  final case class ExternalProposalInvite(id: Id,
                                          externalProposal: ExternalProposal.Id,
                                          email: EmailAddress,
                                          createdAt: Instant,
                                          createdBy: User.Id,
                                          accepted: Option[Meta],
                                          rejected: Option[Meta],
                                          canceled: Option[Meta]) extends StdUserRequest

  final case class Meta(date: Instant, by: User.Id)

  object Timeout {
    val accountValidation: FiniteDuration = 1.day
    val passwordReset: FiniteDuration = 1.hour
  }

}
