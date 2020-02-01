package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.{Info, UserCtx}
import gospeak.core.domain.{ExternalEvent, ExternalProposal, Talk, User}
import gospeak.core.services.storage.ExternalProposalRepo
import gospeak.infra.services.storage.sql.ExternalProposalRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import gospeak.infra.services.storage.sql.utils.{GenericQuery, GenericRepo}
import gospeak.libs.scala.domain.{Done, GMapPlace, Page, Tag}

class ExternalProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ExternalProposalRepo {
  override def create(talk: Talk.Id, event: ExternalEvent.Id, data: ExternalProposal.Data, speakers: NonEmptyList[User.Id])(implicit ctx: UserCtx): IO[ExternalProposal] =
    insert(ExternalProposal(data, talk, event, speakers, Info(ctx.user.id, ctx.now))).run(xa)

  override def edit(id: ExternalProposal.Id)(data: ExternalProposal.Data)(implicit ctx: UserCtx): IO[Done] =
    update(id)(data, ctx.user.id, ctx.now).run(xa)

  override def list(params: Page.Params): IO[Page[ExternalProposal]] = selectPage(params).run(xa)

  override def find(id: ExternalProposal.Id): IO[Option[ExternalProposal]] = selectOne(id).runOption(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object ExternalProposalRepoSql {
  import GenericQuery._
  private val _ = externalProposalIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.externalProposals

  private[sql] def insert(e: ExternalProposal): Insert[ExternalProposal] = {
    val values = fr0"${e.id}, ${e.talk}, ${e.event}, ${e.title}, ${e.duration}, ${e.description}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, " ++ insertInfo(e.info)
    table.insert[ExternalProposal](e, _ => values)
  }

  private[sql] def update(id: ExternalProposal.Id)(e: ExternalProposal.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"title=${e.title}, duration=${e.duration}, description=${e.description}, slides=${e.slides}, video=${e.video}, tags=${e.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, fr0"WHERE id=$id")
  }

  private[sql] def selectOne(id: ExternalProposal.Id): Select[ExternalProposal] =
    table.selectOne[ExternalProposal](fr0"WHERE ep.id=$id", Seq())

  private[sql] def selectPage(params: Page.Params): SelectPage[ExternalProposal] =
    table.selectPage[ExternalProposal](params)

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "ep")), Seq())
}
