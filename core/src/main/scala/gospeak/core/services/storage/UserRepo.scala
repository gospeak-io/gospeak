package gospeak.core.services.storage

import cats.effect.IO
import gospeak.core.domain.utils.{AdminCtx, OrgaCtx, UserAwareCtx, UserCtx}
import gospeak.core.domain.{Group, User}
import gospeak.libs.scala.domain.{EmailAddress, Page}

import java.time.Instant

trait UserRepo extends OrgaUserRepo with SpeakerUserRepo with UserUserRepo with AuthUserRepo with PublicUserRepo with AdminUserRepo with SuggestUserRepo

trait OrgaUserRepo {
  def find(slug: User.Slug): IO[Option[User]]

  def find(id: User.Id): IO[Option[User]]

  def speakers(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[User.Full]]

  def list(ids: List[User.Id]): IO[List[User]]
}

trait SpeakerUserRepo {
  def find(email: EmailAddress): IO[Option[User]]

  def find(slug: User.Slug): IO[Option[User]]

  def list(ids: List[User.Id]): IO[List[User]]
}

trait UserUserRepo {
  def find(id: User.Id): IO[Option[User]]

  def edit(data: User.Data)(implicit ctx: UserCtx): IO[User]

  def editStatus(status: User.Status)(implicit ctx: UserCtx): IO[Unit]

  def list(ids: List[User.Id]): IO[List[User]]
}

trait AuthUserRepo {
  def create(data: User.Data, now: Instant, emailValidated: Option[Instant]): IO[User]

  def edit(user: User.Id)(data: User.Data, now: Instant): IO[User]

  def createLoginRef(login: User.Login, user: User.Id): IO[Unit]

  def createCredentials(credentials: User.Credentials): IO[User.Credentials]

  def editCredentials(login: User.Login)(pass: User.Password): IO[Unit]

  def removeCredentials(login: User.Login): IO[Unit]

  def findCredentials(login: User.Login): IO[Option[User.Credentials]]

  def find(login: User.Login): IO[Option[User]]

  def find(credentials: User.Credentials): IO[Option[User]]

  def find(email: EmailAddress): IO[Option[User]]

  def find(slug: User.Slug): IO[Option[User]]
}

trait PublicUserRepo {
  def speakersPublic(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[User.Full]]

  def speakerCountPublic(group: Group.Id): IO[Long]

  def listAllPublicSlugs()(implicit ctx: UserAwareCtx): IO[List[(User.Id, User.Slug)]]

  def listPublic(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[User.Full]]

  def list(ids: List[User.Id]): IO[List[User]]

  def findPublic(user: User.Slug)(implicit ctx: UserAwareCtx): IO[Option[User.Full]]
}

trait AdminUserRepo {
  def list(params: Page.Params)(implicit ctx: AdminCtx): IO[Page[User.Admin]]
}

trait SuggestUserRepo {
  def speakers(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[User.Full]]
}
