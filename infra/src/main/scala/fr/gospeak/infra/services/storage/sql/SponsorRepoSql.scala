package fr.gospeak.infra.services.storage.sql


import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain._
import fr.gospeak.core.services.storage.SponsorRepo
import fr.gospeak.infra.services.storage.sql.PartnerRepoSql.{fields => partnerFields, table => partnerTable}
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSql.{fields => sponsorPackFields, table => sponsorPackTable}
import fr.gospeak.infra.services.storage.sql.SponsorRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, Page}

class SponsorRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorRepo {
  override def create(group: Group.Id, data: Sponsor.Data, by: User.Id, now: Instant): IO[Sponsor] =
    run(insert, Sponsor(group, data, Info(by, now)))

  override def edit(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): IO[Done] =
    run(update(group, sponsor)(data, by, now))

  override def remove(group: Group.Id, sponsor: Sponsor.Id): IO[Done] =
    run(delete(group, sponsor))

  override def find(group: Group.Id, sponsor: Sponsor.Id): IO[Option[Sponsor]] = run(selectOne(group, sponsor).option)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Sponsor]] = run(Queries.selectPage(selectPage(group, _), params))

  override def listCurrent(group: Group.Id, now: Instant): IO[Seq[Sponsor.Full]] = run(selectCurrent(group, now).to[List])

  override def listAll(group: Group.Id): IO[Seq[Sponsor]] = run(selectAll(group).to[List])

  override def listAll(group: Group.Id, partner: Partner.Id): IO[Seq[Sponsor]] = run(selectAll(group, partner).to[List])
}

object SponsorRepoSql {
  private val _ = sponsorIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table: String = "sponsors"
  private val fields: Seq[String] = Seq("id", "group_id", "partner_id", "sponsor_pack_id", "start", "finish", "paid", "price", "currency", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id")
  private val defaultSort: Page.OrderBy = Page.OrderBy("-start")

  private val tableFullFr = Fragment.const0(s"$table s INNER JOIN $sponsorPackTable sp ON s.sponsor_pack_id=sp.id INNER JOIN $partnerTable p ON s.partner_id=p.id")
  private val fieldsFullFr = Fragment.const0((fields.map("s." + _) ++ sponsorPackFields.map("sp." + _) ++ partnerFields.map("p." + _)).mkString(", "))

  private def values(e: Sponsor): Fragment =
    fr0"${e.id}, ${e.group}, ${e.partner}, ${e.pack}, ${e.start}, ${e.finish}, ${e.paid}, ${e.price.amount}, ${e.price.currency}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"

  private[sql] def insert(elt: Sponsor): doobie.Update0 = buildInsert(tableFr, fieldsFr, values(elt)).update

  private[sql] def update(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"partner_id=${data.partner}, sponsor_pack_id=${data.pack}, start=${data.start}, finish=${data.finish}, paid=${data.paid}, price=${data.price.amount}, currency=${data.price.currency}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, sponsor)).update
  }

  private[sql] def delete(group: Group.Id, sponsor: Sponsor.Id): doobie.Update0 =
    buildDelete(tableFr, where(group, sponsor)).update

  private[sql] def selectOne(group: Group.Id, pack: Sponsor.Id): doobie.Query0[Sponsor] =
    buildSelect(tableFr, fieldsFr, where(group, pack)).query[Sponsor]

  private[sql] def selectPage(group: Group.Id, params: Page.Params): (doobie.Query0[Sponsor], doobie.Query0[Long]) = {
    val page = paginate(params, searchFields, defaultSort, Some(fr0"WHERE group_id=$group"))
    (buildSelect(tableFr, fieldsFr, page.all).query[Sponsor], buildSelect(tableFr, fr0"count(*)", page.where).query[Long])
  }

  private[sql] def selectCurrent(group: Group.Id, now: Instant): doobie.Query0[Sponsor.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr0"WHERE s.group_id=$group AND s.start < $now AND s.finish > $now").query[Sponsor.Full]

  private[sql] def selectAll(group: Group.Id): doobie.Query0[Sponsor] =
    buildSelect(tableFr, fieldsFr, where(group)).query[Sponsor]

  private[sql] def selectAll(group: Group.Id, partner: Partner.Id): doobie.Query0[Sponsor] =
    buildSelect(tableFr, fieldsFr, where(group, partner)).query[Sponsor]

  private def where(group: Group.Id, sponsor: Sponsor.Id): Fragment =
    fr0"WHERE group_id=$group AND id=$sponsor"

  private def where(group: Group.Id): Fragment =
    fr0"WHERE group_id=$group"

  private def where(group: Group.Id, partner: Partner.Id): Fragment =
    fr0"WHERE group_id=$group AND partner_id=$partner"
}
