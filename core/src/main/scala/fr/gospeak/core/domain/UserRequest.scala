package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain.{DataClass, EmailAddress, IId, UuidIdBuilder}

import scala.concurrent.duration._

sealed trait UserRequest {
  val id: UserRequest.Id
  val created: Instant
}

object UserRequest {

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("UserRequest.Id", new Id(_))

  final case class AccountValidationRequest(id: Id,
                                            email: EmailAddress,
                                            deadline: Instant,
                                            created: Instant,
                                            createdBy: User.Id,
                                            accepted: Option[Instant]) extends UserRequest

  final case class PasswordResetRequest(id: Id,
                                        email: EmailAddress,
                                        deadline: Instant,
                                        created: Instant,
                                        accepted: Option[Instant]) extends UserRequest

  final case class UserAskToJoinAGroupRequest(id: Id,
                                              group: Group.Id,
                                              created: Instant,
                                              createdBy: User.Id,
                                              accepted: Option[Meta],
                                              rejected: Option[Meta]) extends UserRequest

  final case class Meta(by: User.Id, date: Instant)

  object Timeout {
    val accountValidation: FiniteDuration = 1.day
    val passwordReset: FiniteDuration = 1.hour
  }

}
