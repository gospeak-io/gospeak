package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.Page

trait GroupRepo {
  def create(data: Group.Data, by: User.Id, now: Instant): IO[Group]

  def find(user: User.Id, slug: Group.Slug): IO[Option[Group]]

  def list(user: User.Id, params: Page.Params): IO[Page[Group]]
}
