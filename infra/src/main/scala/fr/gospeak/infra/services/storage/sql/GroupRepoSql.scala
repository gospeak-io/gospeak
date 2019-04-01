package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Group, User}
import fr.gospeak.core.services.GroupRepo
import fr.gospeak.infra.services.storage.sql.tables.GroupTable
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.Page

class GroupRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with GroupRepo {
  override def create(data: Group.Data, by: User.Id, now: Instant): IO[Group] =
    run(GroupTable.insert, Group(Group.Id.generate(), data.slug, data.name, data.description, NonEmptyList.of(by), Info(by, now)))

  override def find(user: User.Id, slug: Group.Slug): IO[Option[Group]] = run(GroupTable.selectOne(user, slug).option)

  override def list(user: User.Id, params: Page.Params): IO[Page[Group]] = run(Queries.selectPage(GroupTable.selectPage(user, _), params))
}
