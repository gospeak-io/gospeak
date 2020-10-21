package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain.utils.OrgaCtx
import gospeak.core.domain.{Group, Partner, User}
import gospeak.core.services.storage.PartnerRepo
import gospeak.infra.services.storage.sql.PartnerRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.PARTNERS
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.domain.{CustomException, Page}
import gospeak.libs.sql.dsl._

class PartnerRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with PartnerRepo {
  override def create(data: Partner.Data)(implicit ctx: OrgaCtx): IO[Partner] = {
    val partner = Partner(ctx.group.id, data, ctx.info)
    insert(partner).run(xa).map(_ => partner)
  }

  override def edit(partner: Partner.Slug, data: Partner.Data)(implicit ctx: OrgaCtx): IO[Unit] = {
    if (data.slug != partner) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, partner)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have a partner with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, partner)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def remove(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Unit] = delete(ctx.group.id, partner).run(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner]] = selectPage(params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner.Full]] = selectPageFull(params).run(xa)

  override def list(group: Group.Id): IO[List[Partner]] = selectAll(group).run(xa)

  override def list(partners: List[Partner.Id]): IO[List[Partner]] = runNel(selectAll, partners)

  override def find(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Option[Partner]] = selectOne(ctx.group.id, partner).run(xa)

  override def find(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Option[Partner]] = selectOne(ctx.group.id, partner).run(xa)

  override def find(group: Group.Id, partner: Partner.Slug): IO[Option[Partner]] = selectOne(group, partner).run(xa)
}

object PartnerRepoSql {
  val FILTERS: List[Table.Filter] = List(
    Table.Filter.Bool.fromCount("venues", "With venues", VENUES.ID),
    Table.Filter.Bool.fromCount("sponsors", "With sponsors", SPONSORS.ID),
    Table.Filter.Bool.fromCount("contacts", "With contacts", CONTACTS.ID),
    Table.Filter.Bool.fromCount("events", "With events", EVENTS.ID))
  val SORTS: List[Table.Sort] = List(
    Table.Sort("name", TableField("LOWER(pa.name)").asc),
    Table.Sort("sponsor", "last sponsor date", TableField("MAX(s.finish)").desc, TableField("LOWER(pa.name)").asc),
    Table.Sort("event", "last event date", TableField("MAX(s.start)").desc, TableField("LOWER(pa.name)").asc),
    Table.Sort("created", PARTNERS.CREATED_AT.desc),
    Table.Sort("updated", PARTNERS.UPDATED_AT.desc))
  private val PARTNERS_FULL = PARTNERS
    .join(VENUES, _.LeftOuter).on(_.ID is _.PARTNER_ID)
    .join(SPONSORS, _.LeftOuter).on(PARTNERS.ID is _.PARTNER_ID)
    .join(CONTACTS, _.LeftOuter).on(PARTNERS.ID is _.PARTNER_ID)
    .join(EVENTS, _.LeftOuter).on(VENUES.ID is _.VENUE)
    .dropFields(!PARTNERS.has(_))
    .addFields(
      AggField("COALESCE(COUNT(DISTINCT v.id), 0)", "venueCount"),
      AggField("COALESCE(COUNT(DISTINCT s.id), 0)", "sponsorCount"),
      AggField("MAX(s.finish)", "lastSponsorDate"),
      AggField("COALESCE(COUNT(DISTINCT ct.id), 0)", "contactCount"),
      AggField("COALESCE(COUNT(DISTINCT e.id), 0)", "eventCount"),
      AggField("MAX(e.start)", "lastEventDate"))
    .filters(FILTERS)
    .sorts(SORTS)

  private[sql] def insert(e: Partner): Query.Insert[PARTNERS] =
  // PARTNERS.insert.values(e.id, e.group, e.slug, e.name, e.notes, e.description, e.logo,
  //   e.social.facebook, e.social.instagram, e.social.twitter, e.social.linkedIn, e.social.youtube, e.social.meetup, e.social.eventbrite, e.social.slack, e.social.discord, e.social.github,
  //   e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    PARTNERS.insert.values(fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.notes}, ${e.description}, ${e.logo}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(group: Group.Id, partner: Partner.Slug)(d: Partner.Data, by: User.Id, now: Instant): Query.Update[PARTNERS] =
    PARTNERS.update.set(_.SLUG, d.slug).set(_.NAME, d.name).set(_.NOTES, d.notes).set(_.DESCRIPTION, d.description).set(_.LOGO, d.logo)
      .set(_.SOCIAL_FACEBOOK, d.social.facebook).set(_.SOCIAL_INSTAGRAM, d.social.instagram).set(_.SOCIAL_TWITTER, d.social.twitter).set(_.SOCIAL_LINKEDIN, d.social.linkedIn).set(_.SOCIAL_YOUTUBE, d.social.youtube).set(_.SOCIAL_MEETUP, d.social.meetup).set(_.SOCIAL_EVENTBRITE, d.social.eventbrite).set(_.SOCIAL_SLACK, d.social.slack).set(_.SOCIAL_DISCORD, d.social.discord).set(_.SOCIAL_GITHUB, d.social.github)
      .set(_.UPDATED_AT, now).set(_.UPDATED_BY, by)
      .where(where(group, partner))

  private[sql] def delete(group: Group.Id, partner: Partner.Slug): Query.Delete[PARTNERS] =
    PARTNERS.delete.where(where(group, partner))

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Partner] =
    PARTNERS.select.where(_.GROUP_ID is ctx.group.id).page[Partner](params, ctx.toDb)

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Partner.Full] =
    PARTNERS_FULL.select.where(PARTNERS.GROUP_ID is ctx.group.id).page[Partner.Full](params, ctx.toDb)

  private[sql] def selectAll(group: Group.Id): Query.Select.All[Partner] =
    PARTNERS.select.where(_.GROUP_ID is group).all[Partner]

  private[sql] def selectAll(ids: NonEmptyList[Partner.Id]): Query.Select.All[Partner] =
    PARTNERS.select.where(_.ID in ids).all[Partner]

  private[sql] def selectOne(group: Group.Id, partner: Partner.Id): Query.Select.Optional[Partner] =
    PARTNERS.select.where(where(group, partner)).option[Partner]

  private[sql] def selectOne(group: Group.Id, partner: Partner.Slug): Query.Select.Optional[Partner] =
    PARTNERS.select.where(where(group, partner)).option[Partner]

  private def where(group: Group.Id, partner: Partner.Id): Cond = PARTNERS.GROUP_ID.is(group) and PARTNERS.ID.is(partner)

  private def where(group: Group.Id, partner: Partner.Slug): Cond = PARTNERS.GROUP_ID.is(group) and PARTNERS.SLUG.is(partner)
}
