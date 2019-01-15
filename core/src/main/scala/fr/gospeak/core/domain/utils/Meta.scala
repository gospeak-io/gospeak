package fr.gospeak.core.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.User

case class Meta(created: Instant,
                createdBy: User.Id,
                updated: Instant,
                updatedBy: User.Id)

object Meta {
  def apply(by: User.Id): Meta = {
    val now = Instant.now()
    new Meta(now, by, now, by)
  }
}
