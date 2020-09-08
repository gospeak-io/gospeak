package gospeak.core.services.storage

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.utils.{AdminCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.domain.{Group, User}
import gospeak.libs.scala.domain.{Done, Page, Tag}

trait GroupRepo extends OrgaGroupRepo with SpeakerGroupRepo with UserGroupRepo with AuthGroupRepo with PublicGroupRepo with AdminGroupRepo with SuggestGroupRepo

trait OrgaGroupRepo {
  def find(group: Group.Id): IO[Option[Group]]

  def find(group: Group.Slug): IO[Option[Group]]

  def listJoinable(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Group]]

  def exists(slug: Group.Slug): IO[Boolean]

  def create(data: Group.Data)(implicit ctx: UserCtx): IO[Group]

  def edit(data: Group.Data)(implicit ctx: OrgaCtx): IO[Done]

  def addOwner(group: Group.Id, owner: User.Id, by: User.Id)(implicit ctx: UserCtx): IO[Done]

  def removeOwner(owner: User.Id)(implicit ctx: OrgaCtx): IO[Done]

  def listMembers(implicit ctx: OrgaCtx): IO[List[Group.Member]]

  def getStats(implicit ctx: OrgaCtx): IO[Group.Stats]
}

trait SpeakerGroupRepo {
  def find(group: Group.Id): IO[Option[Group]]
}

trait UserGroupRepo {
  def find(group: Group.Id): IO[Option[Group]]

  def list(implicit ctx: UserCtx): IO[List[Group]]

  def listJoined(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Group, Group.Member)]]
}

trait AuthGroupRepo {
  def list(user: User.Id): IO[List[Group]]
}

trait PublicGroupRepo {
  def listAllSlugs()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Group.Slug)]]

  def listFull(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Full]]

  def listFull(user: User.Id): IO[List[Group.Full]]

  def list(ids: List[Group.Id]): IO[List[Group]]

  def find(group: Group.Slug): IO[Option[Group]]

  def findFull(group: Group.Slug): IO[Option[Group.Full]]

  def find(group: Group.Id): IO[Option[Group]]

  def listMembers(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Group.Member]]

  def findActiveMember(group: Group.Id, user: User.Id): IO[Option[Group.Member]]

  def join(group: Group.Id)(user: User, now: Instant): IO[Group.Member]

  def leave(member: Group.Member)(user: User.Id, now: Instant): IO[Done]
}

trait AdminGroupRepo {
  def list(params: Page.Params)(implicit ctx: AdminCtx): IO[Page[Group]]

  def find(group: Group.Id): IO[Option[Group]]
}

trait SuggestGroupRepo {
  def listTags(): IO[List[Tag]]
}
