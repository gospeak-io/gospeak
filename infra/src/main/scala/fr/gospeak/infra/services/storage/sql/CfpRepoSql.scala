package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Cfp, Group, Talk, User}
import fr.gospeak.core.services.CfpRepo
import fr.gospeak.infra.services.storage.sql.tables.CfpTable
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.Page

class CfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CfpRepo {
  override def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp] =
    run(CfpTable.insert, Cfp(Cfp.Id.generate(), group, data.slug, data.name, data.description, Info(by, now)))

  override def find(slug: Cfp.Slug): IO[Option[Cfp]] = run(CfpTable.selectOne(slug).option)

  override def find(id: Cfp.Id): IO[Option[Cfp]] = run(CfpTable.selectOne(id).option)

  override def find(id: Group.Id): IO[Option[Cfp]] = run(CfpTable.selectOne(id).option)

  override def listAvailables(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]] = run(Queries.selectPage(CfpTable.selectPage(talk, _), params))
}
