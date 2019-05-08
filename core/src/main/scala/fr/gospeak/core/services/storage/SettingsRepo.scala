package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.Done

trait SettingsRepo {
  def find(group: Group.Id): IO[Group.Settings]

  def set(group: Group.Id, settings: Group.Settings, by: User.Id, now: Instant): IO[Done]
}
