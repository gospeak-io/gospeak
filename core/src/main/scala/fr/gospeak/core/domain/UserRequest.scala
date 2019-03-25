package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.libs.scalautils.domain.{DataClass, Email, UuidIdBuilder}

import scala.concurrent.duration._

sealed trait UserRequest {
  val id: UserRequest.Id
  val deadline: Instant
  val created: Instant
}

object UserRequest {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("UserRequest.Id", new Id(_))

  final case class EmailValidationRequest(id: Id,
                                          email: Email,
                                          user: User.Id,
                                          deadline: Instant,
                                          created: Instant,
                                          accepted: Option[Instant]) extends UserRequest

  object EmailValidationRequest {
    def apply(email: Email, user: User.Id, now: Instant): EmailValidationRequest =
      new EmailValidationRequest(Id.generate(), email, user, now.plusMillis(1.day.toMillis), now, None)
  }

}
