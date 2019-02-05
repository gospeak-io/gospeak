package fr.gospeak.core.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.User

final case class Info(created: Instant,
                      createdBy: User.Id,
                      updated: Instant,
                      updatedBy: User.Id)

object Info {
  def apply(by: User.Id, now: Instant): Info =
    new Info(now, by, now, by)
}
