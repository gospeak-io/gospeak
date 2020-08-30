package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.{BasicCtx, OrgaCtx}
import gospeak.core.domain.{Group, Partner, User}
import gospeak.core.services.storage.PartnerRepo
import gospeak.infra.services.storage.sql.PartnerRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{CustomException, Done, Page}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}
import org.slf4j.LoggerFactory

class PartnerRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with PartnerRepo {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def create(data: Partner.Data)(implicit ctx: OrgaCtx): IO[Partner] =
    insert(Partner(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(partner: Partner.Slug, data: Partner.Data)(implicit ctx: OrgaCtx): IO[Done] = {
    if (data.slug != partner) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, partner)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, partner)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def remove(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Done] = delete(ctx.group.id, partner).run(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner]] = selectPage(params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner.Full]] = selectPageFull(params).run(xa)

  override def list(group: Group.Id): IO[List[Partner]] = selectAll(group).runList(xa)

  override def list(partners: List[Partner.Id]): IO[List[Partner]] = runNel(selectAll, partners)

  override def find(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Option[Partner]] = selectOne(ctx.group.id, partner).runOption(xa)

  override def find(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Option[Partner]] = selectOne(ctx.group.id, partner).runOption(xa)

  override def find(group: Group.Id, partner: Partner.Slug): IO[Option[Partner]] = selectOne(group, partner).runOption(xa)
}

object PartnerRepoSql {
  private val _ = partnerIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.partners
  val tableFull: Table = table
    .joinOpt(Tables.venues, _.id("pa") -> _.partner_id).get
    .joinOpt(Tables.sponsors, _.id("pa") -> _.partner_id).get
    .joinOpt(Tables.contacts, _.id("pa") -> _.partner_id).get
    .joinOpt(Tables.events, _.id("v") -> _.venue).get
    .dropFields(_.prefix != table.prefix)
    .aggregate("COALESCE(COUNT(DISTINCT v.id), 0)", "venueCount")
    .aggregate("COALESCE(COUNT(DISTINCT s.id), 0)", "sponsorCount")
    .aggregate("MAX(s.finish)", "lastSponsorDate")
    .aggregate("COALESCE(COUNT(DISTINCT ct.id), 0)", "contactCount")
    .aggregate("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount")
    .aggregate("MAX(e.start)", "lastEventDate")
    .copy(filters = List(
      Table.Filter.Bool.fromCount("venues", "With venues", "v.id"),
      Table.Filter.Bool.fromCount("sponsors", "With sponsors", "s.id"),
      Table.Filter.Bool.fromCount("contacts", "With contacts", "ct.id"),
      Table.Filter.Bool.fromCount("events", "With events", "e.id")))
    .setSorts(
      Table.Sort("name", Field("LOWER(pa.name)", "")),
      Table.Sort("sponsor", "last sponsor date", Field("-MAX(s.finish)", ""), Field("LOWER(pa.name)", "")),
      Table.Sort("event", "last event date", Field("-MAX(s.start)", ""), Field("LOWER(pa.name)", "")),
      Table.Sort("created", Field("-created_at", "pa")),
      Table.Sort("updated", Field("-updated_at", "pa")))
  private val tableWithGroup = table
    .join(Tables.groups, _.group_id -> _.id).get
    .dropFields(_.name.startsWith("location_"))

  private[sql] def insert(e: Partner): Query.Insert[Partner] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.notes}, ${e.description}, ${e.logo}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Partner](e, _ => values)
  }

  private[sql] def update(group: Group.Id, partner: Partner.Slug)(d: Partner.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"slug=${d.slug}, name=${d.name}, notes=${d.notes}, description=${d.description}, logo=${d.logo}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"updated_at=$now, updated_by=$by"
    table.update(fields).where(where(group, partner))
  }

  private[sql] def delete(group: Group.Id, partner: Partner.Slug): Query.Delete =
    table.delete.where(where(group, partner))

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Partner] =
    table.selectPage[Partner](params, adapt(ctx)).where(fr0"pa.group_id=${ctx.group.id}")

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Partner.Full] =
    tableFull.selectPage[Partner.Full](params, adapt(ctx)).where(fr0"pa.group_id=${ctx.group.id}")

  private[sql] def selectAll(group: Group.Id): Query.Select[Partner] =
    table.select[Partner].where(fr0"pa.group_id=$group")

  private[sql] def selectAll(ids: NonEmptyList[Partner.Id]): Query.Select[Partner] =
    table.select[Partner].where(Fragments.in(fr"pa.id", ids))

  private[sql] def selectOne(group: Group.Id, partner: Partner.Id): Query.Select[Partner] =
    table.select[Partner].where(where(group, partner))

  private[sql] def selectOne(group: Group.Id, partner: Partner.Slug): Query.Select[Partner] =
    table.select[Partner].where(where(group, partner))

  private def where(group: Group.Id, partner: Partner.Id): Fragment =
    fr0"pa.group_id=$group AND pa.id=$partner"

  private def where(group: Group.Id, partner: Partner.Slug): Fragment =
    fr0"pa.group_id=$group AND pa.slug=$partner"

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
