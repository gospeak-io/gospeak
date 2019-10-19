package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.storage.SponsorRepo
import fr.gospeak.infra.services.storage.sql.SponsorPackRepoSql.{fields => sponsorPackFields, searchFields => sponsorPackSearch, table => sponsorPackTable}
import fr.gospeak.infra.services.storage.sql.SponsorRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.utils.DoobieUtils.SelectPage
import fr.gospeak.libs.scalautils.domain.{Done, Page}

class SponsorRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorRepo {
  override def create(group: Group.Id, data: Sponsor.Data, by: User.Id, now: Instant): IO[Sponsor] =
    run(insert, Sponsor(group, data, Info(by, now)))

  override def edit(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): IO[Done] =
    run(update(group, sponsor)(data, by, now))

  override def remove(group: Group.Id, sponsor: Sponsor.Id): IO[Done] =
    run(delete(group, sponsor))

  override def find(group: Group.Id, sponsor: Sponsor.Id): IO[Option[Sponsor]] = run(selectOne(group, sponsor).option)

  override def listFull(group: Group.Id, params: Page.Params): IO[Page[Sponsor.Full]] = run(selectPage(group, params).page)

  override def listCurrentFull(group: Group.Id, now: Instant): IO[Seq[Sponsor.Full]] = run(selectCurrent(group, now).to[List])

  override def listAll(group: Group.Id): IO[Seq[Sponsor]] = run(selectAll(group).to[List])

  override def listAllFull(group: Group.Id, partner: Partner.Id): IO[Seq[Sponsor.Full]] = run(selectAllFull(group, partner).to[List])
}

object SponsorRepoSql {
  private val _ = sponsorIdMeta // for intellij not remove DoobieUtils.Mappings import
  private[sql] val table: String = "sponsors"
  private val fields: Seq[String] = Seq("id", "group_id", "partner_id", "sponsor_pack_id", "contact_id", "start", "finish", "paid", "price", "currency", "created", "created_by", "updated", "updated_by")
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))
  private val searchFields: Seq[String] = Seq("id")
  private val defaultSort: Page.OrderBy = Page.OrderBy("-start")

  private val tableFull =
    s"$table s " +
      s"INNER JOIN $sponsorPackTable sp ON s.sponsor_pack_id=sp.id " +
      s"INNER JOIN ${Tables.partners.name} ON s.partner_id=pa.id " +
      s"LEFT OUTER JOIN ${Tables.contacts.name} ON s.contact_id=ct.id"
  private val tableFullFr = Fragment.const0(tableFull)
  private val fieldsFullFr = Fragment.const0((
    fields.map("s." + _) ++
      sponsorPackFields.map("sp." + _) ++
      Tables.partners.fields.map(_.value) ++
      Tables.contacts.fields.map(_.value)).mkString(", "))

  private[sql] def insert(e: Sponsor): doobie.Update0 = {
    val values = fr0"${e.id}, ${e.group}, ${e.partner}, ${e.pack}, ${e.contact}, ${e.start}, ${e.finish}, ${e.paid}, ${e.price.amount}, ${e.price.currency}, ${e.info.created}, ${e.info.createdBy}, ${e.info.updated}, ${e.info.updatedBy}"
    buildInsert(tableFr, fieldsFr, values).update
  }

  private[sql] def update(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): doobie.Update0 = {
    val fields = fr0"partner_id=${data.partner}, sponsor_pack_id=${data.pack}, contact_id=${data.contact}, start=${data.start}, finish=${data.finish}, paid=${data.paid}, price=${data.price.amount}, currency=${data.price.currency}, updated=$now, updated_by=$by"
    buildUpdate(tableFr, fields, where(group, sponsor)).update
  }

  private[sql] def delete(group: Group.Id, sponsor: Sponsor.Id): doobie.Update0 =
    buildDelete(tableFr, where(group, sponsor)).update

  private[sql] def selectOne(group: Group.Id, pack: Sponsor.Id): doobie.Query0[Sponsor] =
    buildSelect(tableFr, fieldsFr, where(group, pack)).query[Sponsor]

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Sponsor.Full] = {
    val search = searchFields.map("s." + _) ++ sponsorPackSearch.map("sp." + _) ++ Tables.partners.search.map(_.value) ++ Tables.contacts.search.map(_.value)
    SelectPage[Sponsor.Full](tableFull, fieldsFullFr, fr0"WHERE s.group_id=$group", params, defaultSort, search)
  }

  private[sql] def selectCurrent(group: Group.Id, now: Instant): doobie.Query0[Sponsor.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr0"WHERE s.group_id=$group AND s.start < $now AND s.finish > $now").query[Sponsor.Full]

  private[sql] def selectAll(group: Group.Id): doobie.Query0[Sponsor] =
    buildSelect(tableFr, fieldsFr, where(group)).query[Sponsor]

  private[sql] def selectAllFull(group: Group.Id, partner: Partner.Id): doobie.Query0[Sponsor.Full] =
    buildSelect(tableFullFr, fieldsFullFr, fr"WHERE s.group_id=$group AND s.partner_id=$partner" ++ orderByFragment(defaultSort, Some("s"))).query[Sponsor.Full]

  private def where(group: Group.Id, sponsor: Sponsor.Id): Fragment =
    fr0"WHERE group_id=$group AND id=$sponsor"

  private def where(group: Group.Id): Fragment =
    fr0"WHERE group_id=$group"

  private def where(group: Group.Id, partner: Partner.Id): Fragment =
    fr0"WHERE group_id=$group AND partner_id=$partner"
}
