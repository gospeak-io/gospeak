package gospeak.core.domain.utils

import java.time.Instant

import gospeak.core.domain.User

final case class Info(createdAt: Instant,
                      createdBy: User.Id,
                      updatedAt: Instant,
                      updatedBy: User.Id) {
  def users: List[User.Id] = List(createdBy, updatedBy).distinct
}

object Info {
  def apply(by: User.Id, now: Instant): Info =
    new Info(now, by, now, by)
}
