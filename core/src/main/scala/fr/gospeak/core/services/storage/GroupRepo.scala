package fr.gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.libs.scalautils.domain.{Done, Page, Tag}

trait GroupRepo extends OrgaGroupRepo with SpeakerGroupRepo with UserGroupRepo with AuthGroupRepo with PublicGroupRepo with SuggestGroupRepo

trait OrgaGroupRepo {
  def find(user: User.Id, group: Group.Slug): IO[Option[Group]]

  def find(group: Group.Slug): IO[Option[Group]]

  def listJoinable(user: User.Id, params: Page.Params): IO[Page[Group]]

  def exists(slug: Group.Slug): IO[Boolean]

  def create(data: Group.Data, by: User.Id, now: Instant): IO[Group]

  def edit(slug: Group.Slug)(data: Group.Data, by: User.Id, now: Instant): IO[Done]

  def addOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done]

  def removeOwner(group: Group.Id)(owner: User.Id, by: User.Id, now: Instant): IO[Done]

  def listMembers(implicit ctx: OrgaCtx): IO[Seq[Group.Member]]
}

trait SpeakerGroupRepo {
  def find(group: Group.Id): IO[Option[Group]]
}

trait UserGroupRepo {
  def find(group: Group.Id): IO[Option[Group]]

  def list(user: User.Id): IO[Seq[Group]]

  def listJoined(user: User.Id, params: Page.Params): IO[Page[(Group, Group.Member)]]
}

trait AuthGroupRepo {
  def list(user: User.Id): IO[Seq[Group]]
}

trait PublicGroupRepo {
  def list(params: Page.Params): IO[Page[Group]]

  def list(user: User.Id): IO[Seq[Group]]

  def list(ids: Seq[Group.Id]): IO[Seq[Group]]

  def find(group: Group.Slug): IO[Option[Group]]

  def find(group: Group.Id): IO[Option[Group]]

  def listMembers(group: Group.Id, params: Page.Params): IO[Page[Group.Member]]

  def findActiveMember(group: Group.Id, user: User.Id): IO[Option[Group.Member]]

  def join(group: Group.Id)(user: User, now: Instant): IO[Done]

  def leave(member: Group.Member)(user: User.Id, now: Instant): IO[Done]
}

trait SuggestGroupRepo {
  def find(user: User.Id, slug: Group.Slug): IO[Option[Group]]

  def listTags(): IO[Seq[Tag]]
}
