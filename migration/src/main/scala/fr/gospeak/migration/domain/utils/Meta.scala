package fr.gospeak.migration.domain.utils

import java.time.Instant

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.utils.Info

case class Meta(created: Long, // DateTime,
                createdBy: String, // Person.Id,
                updated: Long, // DateTime,
                updatedBy: String) {
  lazy val toInfo: Info = {
    Info(
      created = Instant.ofEpochMilli(created),
      createdBy = User.Id.from(createdBy).right.get,
      updated = Instant.ofEpochMilli(updated),
      updatedBy = User.Id.from(updatedBy).right.get)
  }
}
