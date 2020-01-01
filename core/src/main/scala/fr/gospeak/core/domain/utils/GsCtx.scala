package fr.gospeak.core.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.{Group, User}

// TODO: use traits which are extended by BasicReq...
sealed class BasicCtx(val now: Instant)

final class UserAwareCtx(override val now: Instant,
                         val user: Option[User]) extends BasicCtx(now)

sealed class UserCtx(override val now: Instant,
                     val user: User) extends BasicCtx(now) {
  def info: Info = Info(user.id, now)
}

final class OrgaCtx(override val now: Instant,
                    override val user: User,
                    val group: Group) extends UserCtx(now, user)
