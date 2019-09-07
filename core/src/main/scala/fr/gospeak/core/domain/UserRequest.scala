package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain.{DataClass, EmailAddress, IId, UuidIdBuilder}

import scala.concurrent.duration._

sealed trait UserRequest {
  val id: UserRequest.Id
  val created: Instant

  def users: Seq[User.Id] = Seq()

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

    override def users: Seq[User.Id] = Seq(createdBy) ++ accepted.map(_.by).toList ++ rejected.map(_.by).toList ++ canceled.map(_.by).toList

    override def isPending(now: Instant): Boolean = accepted.isEmpty && rejected.isEmpty && canceled.isEmpty
  }

  final case class AccountValidationRequest(id: Id,
                                            email: EmailAddress,
                                            deadline: Instant,
                                            created: Instant,
                                            createdBy: User.Id,
                                            accepted: Option[Instant]) extends UserRequest {
    override def users: Seq[User.Id] = Seq(createdBy)

    override def isPending(now: Instant): Boolean = deadline.isAfter(now) && accepted.isEmpty
  }

  final case class PasswordResetRequest(id: Id,
                                        email: EmailAddress,
                                        deadline: Instant,
                                        created: Instant,
                                        accepted: Option[Instant]) extends UserRequest {
    override def isPending(now: Instant): Boolean = deadline.isAfter(now) && accepted.isEmpty
  }

  final case class UserAskToJoinAGroupRequest(id: Id,
                                              group: Group.Id,
                                              created: Instant,
                                              createdBy: User.Id,
                                              accepted: Option[Meta],
                                              rejected: Option[Meta],
                                              canceled: Option[Meta]) extends StdUserRequest

  final case class TalkInvite(id: Id,
                              talk: Talk.Id,
                              email: EmailAddress,
                              created: Instant,
                              createdBy: User.Id,
                              accepted: Option[Meta],
                              rejected: Option[Meta],
                              canceled: Option[Meta]) extends StdUserRequest

  final case class ProposalInvite(id: Id,
                                  proposal: Proposal.Id,
                                  email: EmailAddress,
                                  created: Instant,
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
