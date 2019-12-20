package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.utils.OrgaCtx
import fr.gospeak.core.domain.{Group, Partner, User}
import fr.gospeak.core.services.storage.PartnerRepo
import fr.gospeak.infra.services.storage.sql.PartnerRepoSql._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import fr.gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Insert, Select, SelectPage, Update}
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page}
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

  override def list(group: Group.Id, params: Page.Params): IO[Page[Partner]] = selectPage(group, params).run(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner]] = selectPage(ctx.group.id, params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Partner.Full]] = selectPageFull(ctx.group.id, params).run(xa)

  override def list(group: Group.Id): IO[Seq[Partner]] = selectAll(group).runList(xa)

  override def list(partners: Seq[Partner.Id]): IO[Seq[Partner]] = runNel(selectAll, partners)

  override def find(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[Option[Partner]] = selectOne(ctx.group.id, partner).runOption(xa)

  override def find(partner: Partner.Slug)(implicit ctx: OrgaCtx): IO[Option[Partner]] = selectOne(ctx.group.id, partner).runOption(xa)

  override def find(group: Group.Id, partner: Partner.Slug): IO[Option[Partner]] = selectOne(group, partner).runOption(xa)
}

object PartnerRepoSql {
  private val _ = partnerIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.partners
  private val tableFull = table
    .joinOpt(Tables.venues, _.id("pa") -> _.partner_id).get
    .joinOpt(Tables.sponsors, _.id("pa") -> _.partner_id).get
    .joinOpt(Tables.contacts, _.id("pa") -> _.partner_id).get
    .aggregate("COALESCE(COUNT(DISTINCT v.id), 0)", "venueCount")
    .aggregate("COALESCE(COUNT(DISTINCT s.id), 0)", "sponsorCount")
    .aggregate("COALESCE(COUNT(DISTINCT ct.id), 0)", "contactCount")
    .copy(fields = table.fields)
  private val tableWithGroup = table
    .join(Tables.groups, _.group_id -> _.id).get
    .dropFields(_.name.startsWith("location_"))

  private[sql] def insert(e: Partner): Insert[Partner] = {
    val values = fr0"${e.id}, ${e.group}, ${e.slug}, ${e.name}, ${e.notes}, ${e.description}, ${e.logo}, " ++
      fr0"${e.social.facebook}, ${e.social.instagram}, ${e.social.twitter}, ${e.social.linkedIn}, ${e.social.youtube}, ${e.social.meetup}, ${e.social.eventbrite}, ${e.social.slack}, ${e.social.discord}, ${e.social.github}, " ++
      fr0"${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert[Partner](e, _ => values)
  }

  private[sql] def update(group: Group.Id, partner: Partner.Slug)(d: Partner.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"slug=${d.slug}, name=${d.name}, notes=${d.notes}, description=${d.description}, logo=${d.logo}, " ++
      fr0"social_facebook=${d.social.facebook}, social_instagram=${d.social.instagram}, social_twitter=${d.social.twitter}, social_linkedIn=${d.social.linkedIn}, social_youtube=${d.social.youtube}, social_meetup=${d.social.meetup}, social_eventbrite=${d.social.eventbrite}, social_slack=${d.social.slack}, social_discord=${d.social.discord}, social_github=${d.social.github}, " ++
      fr0"updated_at=$now, updated_by=$by"
    table.update(fields, where(group, partner))
  }

  private[sql] def delete(group: Group.Id, partner: Partner.Slug): Delete =
    table.delete(where(group, partner))

  private[sql] def selectPage(group: Group.Id, params: Page.Params): SelectPage[Partner] =
    table.selectPage[Partner](params, fr0"WHERE pa.group_id=$group")

  private[sql] def selectPageFull(group: Group.Id, params: Page.Params): SelectPage[Partner.Full] = {
    val filters = Seq(
      params.filters.get("venues") match {
        case Some("true") => Some(fr0"COALESCE(COUNT(DISTINCT v.id), 0) > 0")
        case Some("false") => Some(fr0"COALESCE(COUNT(DISTINCT v.id), 0) = 0")
        case _ => None
      },
      params.filters.get("sponsors") match {
        case Some("true") => Some(fr0"COALESCE(COUNT(DISTINCT s.id), 0) > 0")
        case Some("false") => Some(fr0"COALESCE(COUNT(DISTINCT s.id), 0) = 0")
        case _ => None
      },
      params.filters.get("contacts") match {
        case Some("true") => Some(fr0"COALESCE(COUNT(DISTINCT ct.id), 0) > 0")
        case Some("false") => Some(fr0"COALESCE(COUNT(DISTINCT ct.id), 0) = 0")
        case _ => None
      }
    ).flatten
    val having = filters.headOption.map(_ => fr0"HAVING " ++ filters.reduce(_ ++ fr0" AND " ++ _)).getOrElse(fr0"")
    tableFull.selectPage[Partner.Full](params, fr0"WHERE pa.group_id=$group", having)
  }

  private[sql] def selectAll(group: Group.Id): Select[Partner] =
    table.select[Partner](fr0"WHERE pa.group_id=$group")

  private[sql] def selectAll(ids: NonEmptyList[Partner.Id]): Select[Partner] =
    table.select[Partner](fr0"WHERE " ++ Fragments.in(fr"pa.id", ids))

  private[sql] def selectOne(group: Group.Id, partner: Partner.Id): Select[Partner] =
    table.select[Partner](where(group, partner))

  private[sql] def selectOne(group: Group.Id, partner: Partner.Slug): Select[Partner] =
    table.select[Partner](where(group, partner))

  private def where(group: Group.Id, partner: Partner.Id): Fragment =
    fr0"WHERE pa.group_id=$group AND pa.id=$partner"

  private def where(group: Group.Id, partner: Partner.Slug): Fragment =
    fr0"WHERE pa.group_id=$group AND pa.slug=$partner"
}
