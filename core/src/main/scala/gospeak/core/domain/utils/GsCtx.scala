package gospeak.core.domain.utils

import java.time.Instant

import gospeak.core.domain.{Group, User}

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
  val group: Group
}

trait AdminCtx extends UserCtx

object FakeCtx {
  def apply(now: Instant): FakeBasicCtx = FakeBasicCtx(now)

  def apply(now: Instant, user: Option[User]): FakeUserAwareCtx = FakeUserAwareCtx(now, user)

  def apply(now: Instant, user: User): FakeUserCtx = FakeUserCtx(now, user)

  def apply(now: Instant, user: User, group: Group): FakeOrgaCtx = FakeOrgaCtx(now, user, group)
}

final case class FakeBasicCtx(now: Instant) extends BasicCtx

final case class FakeUserAwareCtx(now: Instant, user: Option[User]) extends UserAwareCtx

final case class FakeUserCtx(now: Instant, user: User) extends UserCtx {
  def userAwareCtx: FakeUserAwareCtx = FakeUserAwareCtx(now, Some(user))
}

final case class FakeOrgaCtx(now: Instant, user: User, group: Group) extends OrgaCtx {
  def orgaCtx: OrgaCtx = this

  def userAwareCtx: FakeUserAwareCtx = FakeUserAwareCtx(now, Some(user))

  def userCtx: FakeUserCtx = FakeUserCtx(now, user)
}

final case class FakeAdminCtx(now: Instant, user: User) extends AdminCtx {
  def userAwareCtx: FakeUserAwareCtx = FakeUserAwareCtx(now, Some(user))

  def userCtx: FakeUserCtx = FakeUserCtx(now, user)
}
