package fr.gospeak.core.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.{Group, User}

sealed class BasicCtx(val now: Instant)

class UserCtx(override val now: Instant,
              val user: User) extends BasicCtx(now) {
  def info: Info = Info(user.id, now)
}

class OrgaCtx(override val now: Instant,
              override val user: User,
              val group: Group) extends UserCtx(now, user)
