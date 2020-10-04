package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.syntax.string._
import doobie.util.fragment.Fragment
import gospeak.core.domain._
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx}
import gospeak.core.services.storage.SponsorRepo
import gospeak.infra.services.storage.sql.SponsorRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.SPONSORS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.{Done, Page}
import gospeak.libs.sql.doobie.{DbCtx, Query, Table}
import gospeak.libs.sql.dsl
import gospeak.libs.sql.dsl.Cond

class SponsorRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorRepo {
  override def create(data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Sponsor] =
    insert(Sponsor(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(sponsor: Sponsor.Id, data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Done] =
    update(ctx.group.id, sponsor)(data, ctx.user.id, ctx.now).run(xa)

  override def remove(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Done] = delete(ctx.group.id, sponsor).run(xa)

  override def find(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Option[Sponsor]] = selectOne(ctx.group.id, sponsor).runOption(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Sponsor.Full]] = selectPage(params).run(xa)

  override def listCurrentFull(group: Group.Id, now: Instant): IO[List[Sponsor.Full]] = selectCurrent(group, now).runList(xa)

  override def listAll(implicit ctx: OrgaCtx): IO[List[Sponsor]] = selectAll(ctx.group.id).runList(xa)

  override def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[List[Sponsor]] = selectAll(ctx.group.id, contact).runList(xa)

  override def listAllFull(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[List[Sponsor.Full]] = selectAllFull(ctx.group.id, partner).runList(xa)
}

object SponsorRepoSql {
  private val _ = sponsorIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.sponsors
  val tableFull: Table = table
    .join(Tables.sponsorPacks, _.sponsor_pack_id -> _.id).get
    .join(Tables.partners, _.partner_id -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id -> _.id).get
    .filters(Table.Filter.Bool.fromNow("active", "Is active", "s.start", "s.finish"))
  private val SPONSORS_FULL = SPONSORS.joinOn(_.SPONSOR_PACK_ID).joinOn(SPONSORS.PARTNER_ID).joinOn(SPONSORS.CONTACT_ID)
    .filters(dsl.Table.Filter.Bool.fromNow("active", "Is active", SPONSORS.START, SPONSORS.FINISH))

  private[sql] def insert(e: Sponsor): Query.Insert[Sponsor] = {
    val values = fr0"${e.id}, ${e.group}, ${e.partner}, ${e.pack}, ${e.contact}, ${e.start}, ${e.finish}, ${e.paid}, ${e.price.amount}, ${e.price.currency}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[Sponsor](e, _ => values)
    val q2 = SPONSORS.insert.values(e.id, e.group, e.partner, e.pack, e.contact, e.start, e.finish, e.paid, e.price.amount, e.price.currency, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"partner_id=${data.partner}, sponsor_pack_id=${data.pack}, contact_id=${data.contact}, start=${data.start}, finish=${data.finish}, paid=${data.paid}, price=${data.price.amount}, currency=${data.price.currency}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(where(group, sponsor))
    val q2 = SPONSORS.update.set(_.PARTNER_ID, data.partner).set(_.SPONSOR_PACK_ID, data.pack).set(_.CONTACT_ID, data.contact).set(_.START, data.start).set(_.FINISH, data.finish).set(_.PAID, data.paid).set(_.PRICE, data.price.amount).set(_.CURRENCY, data.price.currency).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, sponsor))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def delete(group: Group.Id, sponsor: Sponsor.Id): Query.Delete = {
    val q1 = table.delete.where(where(group, sponsor))
    val q2 = SPONSORS.delete.where(where2(group, sponsor))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(group: Group.Id, pack: Sponsor.Id): Query.Select[Sponsor] = {
    val q1 = table.select[Sponsor].where(where(group, pack))
    val q2 = SPONSORS.select.where(where2(group, pack)).option[Sponsor]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Sponsor.Full] = {
    val q1 = tableFull.selectPage[Sponsor.Full](params, adapt(ctx)).where(fr0"s.group_id=${ctx.group.id}")
    val q2 = SPONSORS_FULL.select.where(SPONSORS.GROUP_ID is ctx.group.id).page[Sponsor.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectCurrent(group: Group.Id, now: Instant): Query.Select[Sponsor.Full] = {
    val date = TimeUtils.toLocalDate(now)
    val q1 = tableFull.select[Sponsor.Full].where(fr0"s.group_id=$group AND s.start < $now AND s.finish > $now")
    val q2 = SPONSORS_FULL.select.where(SPONSORS.GROUP_ID.is(group) and SPONSORS.START.lt(date) and SPONSORS.FINISH.gt(date)).all[Sponsor.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(group: Group.Id): Query.Select[Sponsor] = {
    val q1 = table.select[Sponsor].where(fr0"s.group_id=$group")
    val q2 = SPONSORS.select.where(_.GROUP_ID is group).all[Sponsor]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Query.Select[Sponsor] = {
    val q1 = table.select[Sponsor].where(fr0"s.group_id=$group AND s.contact_id=$contact")
    val q2 = SPONSORS.select.where(s => s.GROUP_ID.is(group) and s.CONTACT_ID.is(contact)).all[Sponsor]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllFull(group: Group.Id, partner: Partner.Id): Query.Select[Sponsor.Full] = {
    val q1 = tableFull.select[Sponsor.Full].where(fr0"s.group_id=$group AND s.partner_id=$partner")
    val q2 = SPONSORS_FULL.select.where(SPONSORS.GROUP_ID.is(group) and SPONSORS.PARTNER_ID.is(partner)).all[Sponsor.Full]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(group: Group.Id, sponsor: Sponsor.Id): Fragment = fr0"s.group_id=$group AND s.id=$sponsor"

  private def where2(group: Group.Id, sponsor: Sponsor.Id): Cond = SPONSORS.GROUP_ID.is(group) and SPONSORS.ID.is(sponsor)

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
