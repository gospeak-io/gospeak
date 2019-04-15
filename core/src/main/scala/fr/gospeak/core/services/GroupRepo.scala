package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.Page

trait GroupRepo extends OrgaGroupRepo with SpeakerGroupRepo with UserGroupRepo with AuthGroupRepo with PublicGroupRepo

trait OrgaGroupRepo {
  def find(user: User.Id, slug: Group.Slug): IO[Option[Group]]
}

trait SpeakerGroupRepo

trait UserGroupRepo {
  def create(data: Group.Data, by: User.Id, now: Instant): IO[Group]

  def list(user: User.Id, params: Page.Params): IO[Page[Group]]
}

trait AuthGroupRepo {
  def list(user: User.Id): IO[Seq[Group]]
}

trait PublicGroupRepo {
  def listPublic(params: Page.Params): IO[Page[Group]]

  def findPublic(group: Group.Slug): IO[Option[Group]]
}
