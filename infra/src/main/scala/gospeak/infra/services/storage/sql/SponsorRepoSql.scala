package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.syntax.string._
import fr.loicknuchel.safeql.{Cond, Query, Table}
import gospeak.core.domain._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.services.storage.SponsorRepo
import gospeak.infra.services.storage.sql.SponsorRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.SPONSORS
import gospeak.infra.services.storage.sql.database.tables.SPONSORS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain.Page

class SponsorRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorRepo {
  override def create(data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Sponsor] = {
    val sponsor = Sponsor(ctx.group.id, data, ctx.info)
    insert(sponsor).run(xa).map(_ => sponsor)
  }

  override def edit(sponsor: Sponsor.Id, data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Unit] =
    update(ctx.group.id, sponsor)(data, ctx.user.id, ctx.now).run(xa)

  override def remove(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Unit] = delete(ctx.group.id, sponsor).run(xa)

  override def find(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Option[Sponsor]] = selectOne(ctx.group.id, sponsor).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Sponsor.Full]] = selectPage(params).run(xa).map(_.fromSql)

  override def listCurrentFull(group: Group.Id, now: Instant): IO[List[Sponsor.Full]] = selectCurrent(group, now).run(xa)

  override def listAll(implicit ctx: OrgaCtx): IO[List[Sponsor]] = selectAll(ctx.group.id).run(xa)

  override def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[List[Sponsor]] = selectAll(ctx.group.id, contact).run(xa)

  override def listAllFull(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[List[Sponsor.Full]] = selectAllFull(ctx.group.id, partner).run(xa)
}

object SponsorRepoSql {
  val FILTERS = List(Table.Filter.Bool.fromNow("active", "Is active", SPONSORS.START, SPONSORS.FINISH))
  val SPONSORS_FULL: Table.JoinTable = SPONSORS.joinOn(_.SPONSOR_PACK_ID).joinOn(SPONSORS.PARTNER_ID).joinOn(SPONSORS.CONTACT_ID).filters(FILTERS)

  private[sql] def insert(e: Sponsor): Query.Insert[SPONSORS] =
  // SPONSORS.insert.values(e.id, e.group, e.partner, e.pack, e.contact, e.start, e.finish, e.paid, e.price.amount, e.price.currency, e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    SPONSORS.insert.values(fr0"${e.id}, ${e.group}, ${e.partner}, ${e.pack}, ${e.contact}, ${e.start}, ${e.finish}, ${e.paid}, ${e.price.amount}, ${e.price.currency}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): Query.Update[SPONSORS] =
    SPONSORS.update.set(_.PARTNER_ID, data.partner).set(_.SPONSOR_PACK_ID, data.pack).set(_.CONTACT_ID, data.contact).set(_.START, data.start).set(_.FINISH, data.finish).set(_.PAID, data.paid).set(_.PRICE, data.price.amount).set(_.CURRENCY, data.price.currency).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, sponsor))

  private[sql] def delete(group: Group.Id, sponsor: Sponsor.Id): Query.Delete[SPONSORS] =
    SPONSORS.delete.where(where(group, sponsor))

  private[sql] def selectOne(group: Group.Id, pack: Sponsor.Id): Query.Select.Optional[Sponsor] =
    SPONSORS.select.where(where(group, pack)).option[Sponsor]

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Sponsor.Full] =
    SPONSORS_FULL.select.where(SPONSORS.GROUP_ID is ctx.group.id).page[Sponsor.Full](params.toSql, ctx.toSql)

  private[sql] def selectCurrent(group: Group.Id, now: Instant): Query.Select.All[Sponsor.Full] = {
    val date = TimeUtils.toLocalDate(now)
    SPONSORS_FULL.select.where(SPONSORS.GROUP_ID.is(group) and SPONSORS.START.lt(date) and SPONSORS.FINISH.gt(date)).all[Sponsor.Full]
  }

  private[sql] def selectAll(group: Group.Id): Query.Select.All[Sponsor] =
    SPONSORS.select.where(_.GROUP_ID is group).all[Sponsor]

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Query.Select.All[Sponsor] =
    SPONSORS.select.where(s => s.GROUP_ID.is(group) and s.CONTACT_ID.is(contact)).all[Sponsor]

  private[sql] def selectAllFull(group: Group.Id, partner: Partner.Id): Query.Select.All[Sponsor.Full] =
    SPONSORS_FULL.select.where(SPONSORS.GROUP_ID.is(group) and SPONSORS.PARTNER_ID.is(partner)).all[Sponsor.Full]

  private def where(group: Group.Id, sponsor: Sponsor.Id): Cond = SPONSORS.GROUP_ID.is(group) and SPONSORS.ID.is(sponsor)
}
