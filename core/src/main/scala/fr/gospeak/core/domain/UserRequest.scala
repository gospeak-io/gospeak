package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain.{DataClass, EmailAddress, UuidIdBuilder}

import scala.concurrent.duration._

sealed trait UserRequest {
  val id: UserRequest.Id
  val created: Instant
  val deadline: Instant
}

object UserRequest {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("UserRequest.Id", new Id(_))

  final case class AccountValidationRequest(id: Id,
                                            email: EmailAddress,
                                            user: User.Id,
                                            created: Instant,
                                            deadline: Instant,
                                            accepted: Option[Instant]) extends UserRequest

  object AccountValidationRequest {
    def apply(email: EmailAddress, user: User.Id, now: Instant): AccountValidationRequest =
      new AccountValidationRequest(Id.generate(), email, user, now, now.plusMillis(1.day.toMillis), None)
  }

  final case class PasswordResetRequest(id: Id,
                                        email: EmailAddress,
                                        created: Instant,
                                        deadline: Instant,
                                        accepted: Option[Instant]) extends UserRequest

  object PasswordResetRequest {
    def apply(email: EmailAddress, now: Instant): PasswordResetRequest =
      new PasswordResetRequest(Id.generate(), email, now, now.plusMillis(1.hour.toMillis), None)
  }

}
