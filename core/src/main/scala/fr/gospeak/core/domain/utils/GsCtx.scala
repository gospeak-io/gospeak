package fr.gospeak.core.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.{Group, User}

trait BasicCtx {
  val now: Instant
}

trait UserAwareCtx extends BasicCtx {
  val user: Option[User]
}

trait UserCtx extends BasicCtx {
  val user: User

  def info: Info = Info(user.id, now)
}

trait OrgaCtx extends UserCtx {
  val user: User
  val group: Group
}

object FakeCtx {
  def apply(now: Instant): BasicCtx = FakeBasicCtx(now)

  def apply(now: Instant, user: User): UserCtx = FakeUserCtx(now, user)

  def apply(now: Instant, user: User, group: Group): OrgaCtx = FakeOrgaCtx(now, user, group)
}

final case class FakeBasicCtx(now: Instant) extends BasicCtx

final case class FakeUserCtx(now: Instant, user: User) extends UserCtx

final case class FakeOrgaCtx(now: Instant, user: User, group: Group) extends OrgaCtx
