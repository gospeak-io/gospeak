package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.CfpRepo
import fr.gospeak.infra.services.storage.sql.CfpRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page, Tag}

class CfpRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with CfpRepo {
  override def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp] =
    insert(Cfp(group, data, Info(by, now))).run(xa)

  override def edit(group: Group.Id, cfp: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != cfp) {
      find(group, data.slug).flatMap {
        case None => update(group, cfp)(data, by, now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a cfp with slug ${data.slug}"))
      }
    } else {
      update(group, cfp)(data, by, now).run(xa)
    }
  }

  override def find(id: Cfp.Id): IO[Option[Cfp]] = selectOne(id).runOption(xa)

  override def find(slug: Cfp.Slug): IO[Option[Cfp]] = selectOne(slug).runOption(xa)

  override def find(group: Group.Id, slug: Cfp.Slug): IO[Option[Cfp]] = selectOne(group, slug).runOption(xa)

  override def find(id: Event.Id): IO[Option[Cfp]] = selectOne(id).runOption(xa)

  override def findOpen(slug: Cfp.Slug, now: Instant): IO[Option[Cfp]] = selectOne(slug, now).runOption(xa)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Cfp]] = selectPage(group, params).run(xa)

  override def availableFor(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]] = selectPage(talk, params).run(xa)

  override def listOpen(now: Instant, params: Page.Params): IO[Page[Cfp]] = selectPage(now, params).run(xa)

  override def list(ids: Seq[Cfp.Id]): IO[Seq[Cfp]] = runNel(selectAll, ids)

  override def list(group: Group.Id): IO[Seq[Cfp]] = selectAll(group).runList(xa)

  override def listAllOpen(group: Group.Id, now: Instant): IO[Seq[Cfp]] = selectAll(group, now).runList(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object CfpRepoSql {
  private val _ = cfpIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.cfps
  private val tableWithEvent = table.join(Tables.events, _.field("id"), _.field("cfp_id")).get

  private[sql] def insert(e: Cfp): Insert[Cfp] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.begin}, ${e.close}, ${e.description}, ${e.tags}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    table.insert[Cfp](e, _ => values)
  }

  private[sql] def update(group: Group.Id, slug: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"c.slug=${data.slug}, c.name=${data.name}, c.begin=${data.begin}, c.close=${data.close}, c.description=${data.description}, c.tags=${data.tags}, c.updated=$now, c.updated_by=$by"
    table.update(fields, where(group, slug))
  }

  private[sql] def selectOne(id: Cfp.Id): Select[Cfp] =
    table.select[Cfp](fr0"WHERE c.id=$id")

  private[sql] def selectOne(slug: Cfp.Slug): Select[Cfp] =
    table.select[Cfp](fr0"WHERE c.slug=$slug")

  private[sql] def selectOne(group: Group.Id, slug: Cfp.Slug): Select[Cfp] =
    table.select[Cfp](where(group, slug))

  private[sql] def selectOne(id: Event.Id): Select[Cfp] =
    tableWithEvent.select[Cfp](Tables.cfps.fields, fr0"WHERE e.id=$id")

  private[sql] def selectOne(slug: Cfp.Slug, now: Instant): Select[Cfp] =
    table.select[Cfp](where(slug, now))

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Cfp] =
    table.selectPage[Cfp](params, fr0"WHERE c.group_id=$group")

  private[sql] def selectPage(talk: Talk.Id, params: Page.Params): SelectPage[Cfp] = {
    val talkCfps = Tables.proposals.select(Seq(Field("cfp_id", "p")), fr0"WHERE p.talk_id=$talk", Seq())
    table.selectPage[Cfp](params, fr0"WHERE c.id NOT IN (" ++ talkCfps.fr ++ fr0")")
  }

  private[sql] def selectPage(now: Instant, params: Page.Params): SelectPage[Cfp] =
    table.selectPage[Cfp](params, where(now))

  private[sql] def selectAll(group: Group.Id): Select[Cfp] =
    table.select[Cfp](fr0"WHERE c.group_id=$group")

  private[sql] def selectAll(ids: NonEmptyList[Cfp.Id]): Select[Cfp] =
    table.select[Cfp](fr0"WHERE " ++ Fragments.in(fr"c.id", ids))

  private[sql] def selectAll(group: Group.Id, now: Instant): Select[Cfp] =
    table.select[Cfp](where(group, now))

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "c")), Seq())

  private def where(group: Group.Id, slug: Cfp.Slug): Fragment =
    fr0"WHERE c.group_id=$group AND c.slug=$slug"

  private def where(now: Instant): Fragment =
    fr0"WHERE (c.begin IS NULL OR c.begin < $now) AND (c.close IS NULL OR c.close > $now)"

  private def where(slug: Cfp.Slug, now: Instant): Fragment =
    where(now) ++ fr0" AND c.slug=$slug"

  private def where(group: Group.Id, now: Instant): Fragment =
    where(now) ++ fr0" AND c.group_id=$group"
}
