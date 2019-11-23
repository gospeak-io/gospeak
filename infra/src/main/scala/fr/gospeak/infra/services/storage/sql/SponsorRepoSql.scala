package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.services.storage.SponsorRepo
import fr.gospeak.infra.services.storage.sql.SponsorRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Insert, Select, SelectPage, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{Done, Page}

class SponsorRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with SponsorRepo {
  override def create(data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Sponsor] =
    insert(Sponsor(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(sponsor: Sponsor.Id, data: Sponsor.Data)(implicit ctx: OrgaCtx): IO[Done] =
    update(ctx.group.id, sponsor)(data, ctx.user.id, ctx.now).run(xa)

  override def remove(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Done] = delete(ctx.group.id, sponsor).run(xa)

  override def find(sponsor: Sponsor.Id)(implicit ctx: OrgaCtx): IO[Option[Sponsor]] = selectOne(ctx.group.id, sponsor).runOption(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Sponsor.Full]] = selectPage(ctx.group.id, params).run(xa)

  override def listCurrentFull(group: Group.Id, now: Instant): IO[Seq[Sponsor.Full]] = selectCurrent(group, now).runList(xa)

  override def listAll(group: Group.Id): IO[Seq[Sponsor]] = selectAll(group).runList(xa)

  override def listAll(contact: Contact.Id)(implicit ctx: OrgaCtx): IO[Seq[Sponsor]] = selectAll(ctx.group.id, contact).runList(xa)

  override def listAllFull(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Seq[Sponsor.Full]] = selectAllFull(ctx.group.id, partner).runList(xa)
}

object SponsorRepoSql {
  private val _ = sponsorIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.sponsors
  private val tableFull = table
    .join(Tables.sponsorPacks, _.sponsor_pack_id -> _.id).get
    .join(Tables.partners, _.partner_id -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id -> _.id).get

  private[sql] def insert(e: Sponsor): Insert[Sponsor] = {
    val values = fr0"${e.id}, ${e.group}, ${e.partner}, ${e.pack}, ${e.contact}, ${e.start}, ${e.finish}, ${e.paid}, ${e.price.amount}, ${e.price.currency}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(group: Group.Id, sponsor: Sponsor.Id)(data: Sponsor.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"partner_id=${data.partner}, sponsor_pack_id=${data.pack}, contact_id=${data.contact}, start=${data.start}, finish=${data.finish}, paid=${data.paid}, price=${data.price.amount}, currency=${data.price.currency}, updated_at=$now, updated_by=$by"
    table.update(fields, where(group, sponsor))
  }

  private[sql] def delete(group: Group.Id, sponsor: Sponsor.Id): Delete =
    table.delete(where(group, sponsor))

  private[sql] def selectOne(group: Group.Id, pack: Sponsor.Id): Select[Sponsor] =
    table.select[Sponsor](where(group, pack))

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Sponsor.Full] =
    tableFull.selectPage[Sponsor.Full](params, fr0"WHERE s.group_id=$group")

  private[sql] def selectCurrent(group: Group.Id, now: Instant): Select[Sponsor.Full] =
    tableFull.select[Sponsor.Full](fr0"WHERE s.group_id=$group AND s.start < $now AND s.finish > $now")

  private[sql] def selectAll(group: Group.Id): Select[Sponsor] =
    table.select[Sponsor](fr0"WHERE s.group_id=$group")

  private[sql] def selectAll(group: Group.Id, contact: Contact.Id): Select[Sponsor] =
    table.select[Sponsor](fr0"WHERE s.group_id=$group AND s.contact_id=$contact")

  private[sql] def selectAllFull(group: Group.Id, partner: Partner.Id): Select[Sponsor.Full] =
    tableFull.select[Sponsor.Full](fr0"WHERE s.group_id=$group AND s.partner_id=$partner")

  private def where(group: Group.Id, sponsor: Sponsor.Id): Fragment =
    fr0"WHERE s.group_id=$group AND s.id=$sponsor"
}
