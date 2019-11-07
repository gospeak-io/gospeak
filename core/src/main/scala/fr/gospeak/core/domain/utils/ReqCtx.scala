package fr.gospeak.core.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.{Group, User}

sealed trait ReqCtx {
  val now: Instant
}

sealed trait SecuredReqCtx extends ReqCtx {
  val user: User

  def info: Info = Info(user.id, now)
}

class OrgaReqCtx(val now: Instant,
                 val user: User,
                 val group: Group) extends SecuredReqCtx

class UserReqCtx(val now: Instant,
                 val user: User) extends SecuredReqCtx

class AnonymousReqCtx(val now: Instant) extends ReqCtx
