package gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.Event.Rsvp.Answer
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils._
import gospeak.core.services.storage.EventRepo
import gospeak.infra.services.storage.sql.EventRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain._
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}
import gospeak.libs.sql.dsl.{AggField, Cond}

class EventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with EventRepo {
  override def create(data: Event.Data)(implicit ctx: OrgaCtx): IO[Event] =
    insert(Event.create(ctx.group.id, data, ctx.info)).run(xa)

  override def edit(event: Event.Slug, data: Event.Data)(implicit ctx: OrgaCtx): IO[Done] = {
    if (data.slug != event) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, event)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, event)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def editDescription(event: Event.Id, description: LiquidMarkdown[Message.EventInfo])(implicit ctx: AdminCtx): IO[Done] =
    updateDescription(event)(description).run(xa)

  override def editNotes(event: Event.Slug, notes: String)(implicit ctx: OrgaCtx): IO[Done] =
    updateNotes(ctx.group.id, event)(notes, ctx.user.id, ctx.now).run(xa)

  override def attachCfp(event: Event.Slug, cfp: Cfp.Id)(implicit ctx: OrgaCtx): IO[Done] =
    updateCfp(ctx.group.id, event)(cfp, ctx.user.id, ctx.now).run(xa)

  override def editTalks(event: Event.Slug, talks: List[Proposal.Id])(implicit ctx: OrgaCtx): IO[Done] =
    updateTalks(ctx.group.id, event)(talks, ctx.user.id, ctx.now).run(xa)

  override def publish(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Done] =
    updatePublished(ctx.group.id, event)(ctx.user.id, ctx.now).run(xa)

  override def find(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event]] = selectOne(ctx.group.id, event).runOption(xa)

  override def find(event: Event.Id)(implicit ctx: AdminCtx): IO[Option[Event]] = selectOne(event).runOption(xa)

  override def findFull(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event.Full]] = selectOneFull(ctx.group.id, event).runOption(xa)

  override def findPublished(group: Group.Id, event: Event.Slug): IO[Option[Event.Full]] = selectOnePublished(group, event).runOption(xa)

  override def findFull(group: Group.Slug, event: Event.Slug)(implicit ctx: UserAwareCtx): IO[Option[Event.Full]] = selectOneFull(group, event).runOption(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]] = selectPage(params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]] = selectPageFull(params).run(xa)

  override def list(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[List[Event]] = selectAll(ctx.group.id, venue).runList(xa)

  override def list(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[List[(Event, Venue)]] = selectAll(ctx.group.id, partner).runList(xa)

  override def listAllPublishedSlugs()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Event.Slug)]] = selectAllPublishedSlugs().runList(xa)

  override def listPublished(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Event.Full]] = selectPagePublished(group, params).run(xa)

  override def list(ids: List[Event.Id]): IO[List[Event]] = runNel(selectAll, ids)

  override def listAllFromGroups(groups: List[Group.Id])(implicit ctx: AdminCtx): IO[List[Event]] = runNel[Group.Id, Event](selectAllFromGroups(_), groups)

  override def listAfter(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]] = selectPageAfterFull(params).run(xa)

  override def listIncoming(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Event.Full, Option[Event.Rsvp])]] = selectPageIncoming(params).run(xa)

  override def countYesRsvp(event: Event.Id): IO[Long] = countRsvp(event, Event.Rsvp.Answer.Yes).runOption(xa).map(_.getOrElse(0))

  override def listRsvps(event: Event.Id): IO[List[Event.Rsvp]] = selectAllRsvp(event).runList(xa)

  override def listRsvps(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): IO[List[Event.Rsvp]] = selectAllRsvp(event, answers).runList(xa)

  override def findRsvp(event: Event.Id, user: User.Id): IO[Option[Event.Rsvp]] = selectOneRsvp(event, user).runOption(xa)

  override def findFirstWait(event: Event.Id): IO[Option[Event.Rsvp]] = selectFirstRsvp(event, Answer.Wait).runOption(xa)

  override def createRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done] =
    insertRsvp(Event.Rsvp(event, answer, now, user)).run(xa).map(_ => Done)

  override def editRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Done] = updateRsvp(event, user.id, answer, now).run(xa)

  override def listTags(): IO[List[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object EventRepoSql {
  private val _ = eventIdMeta // for intellij not remove DoobieMappings import
  private val table = Tables.events
  private val tableWithVenue = table
    .joinOpt(Tables.venues, _.venue -> _.id).get
    .dropFields(_.name.startsWith("address_"))
  private val tableFull = tableWithVenue
    .joinOpt(Tables.partners, _.partner_id("v") -> _.id).get
    .joinOpt(Tables.contacts, _.contact_id("v") -> _.id).get
    .joinOpt(Tables.cfps, _.cfp_id("e") -> _.id).get
    .join(Tables.groups.dropFields(_.name.startsWith("location_")), _.group_id("e") -> _.id).get
  private val rsvpTable = Tables.eventRsvps
  private val rsvpTableWithUser = rsvpTable
    .join(Tables.users, _.user_id -> _.id).flatMap(_.dropField(_.user_id)).get
  private val tableFullWithMemberAndRsvp = tableFull
    .join(Tables.groupMembers, _.id("g") -> _.group_id).get
    .joinOpt(rsvpTable, _.id("e") -> _.event_id, _.user_id("gm") -> _.user_id).get
    .joinOpt(Tables.users, _.user_id("er") -> _.id).get
    .dropField(_.user_id("er")).get
    .dropFields(_.prefix == "gm")
  private val EVENTS_WITH_VENUES = EVENTS.joinOn(_.VENUE).dropFields(_.name.startsWith("address_"))
  private val EVENTS_FULL = EVENTS_WITH_VENUES
    .joinOn(VENUES.PARTNER_ID, _.LeftOuter)
    .joinOn(VENUES.CONTACT_ID)
    .joinOn(EVENTS.CFP_ID)
    .joinOn(EVENTS.GROUP_ID).dropFields(_.name.startsWith("location_"))
  private val RSVP_WITH_USERS = EVENT_RSVPS.joinOn(_.USER_ID).dropFields(EVENT_RSVPS.USER_ID)
  private val EVENTS_FULL_WITH_MEMBERS_AND_RSVPS = EVENTS_FULL
    .joinOn(GROUP_MEMBERS.GROUP_ID).dropFields(GROUP_MEMBERS.getFields)
    .join(EVENT_RSVPS, _.LeftOuter).on(r => EVENTS.ID.is(r.EVENT_ID) and GROUP_MEMBERS.USER_ID.is(r.USER_ID))
    .joinOn(EVENT_RSVPS.USER_ID, _.LeftOuter).dropFields(EVENT_RSVPS.USER_ID)

  private[sql] def insert(e: Event): Query.Insert[Event] = {
    val values = fr0"${e.id}, ${e.group}, ${e.cfp}, ${e.slug}, ${e.name}, ${e.kind}, ${e.start}, ${e.maxAttendee}, ${e.allowRsvp}, ${e.description}, ${e.orgaNotes.text}, ${e.orgaNotes.updatedAt}, ${e.orgaNotes.updatedBy}, ${e.venue}, ${e.talks}, ${e.tags}, ${e.published}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.event)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    val q1 = table.insert[Event](e, _ => values)
    val q2 = EVENTS.insert.values(e.id, e.group, e.cfp, e.slug, e.name, e.kind, e.start, e.maxAttendee, e.allowRsvp, e.description, e.orgaNotes.text, e.orgaNotes.updatedAt, e.orgaNotes.updatedBy, e.venue, e.talks, e.tags, e.published, e.refs.meetup.map(_.group), e.refs.meetup.map(_.event), e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def update(group: Group.Id, event: Event.Slug)(d: Event.Data, by: User.Id, now: Instant): Query.Update = {
    val fields = fr0"cfp_id=${d.cfp}, slug=${d.slug}, name=${d.name}, kind=${d.kind}, start=${d.start}, max_attendee=${d.maxAttendee}, allow_rsvp=${d.allowRsvp}, description=${d.description}, venue=${d.venue}, tags=${d.tags}, meetupGroup=${d.refs.meetup.map(_.group)}, meetupEvent=${d.refs.meetup.map(_.event)}, updated_at=$now, updated_by=$by"
    val q1 = table.update(fields).where(where(group, event))
    val q2 = EVENTS.update.set(_.CFP_ID, d.cfp).set(_.SLUG, d.slug).set(_.NAME, d.name).set(_.KIND, d.kind).set(_.START, d.start).set(_.MAX_ATTENDEE, d.maxAttendee).set(_.ALLOW_RSVP, d.allowRsvp).set(_.DESCRIPTION, d.description).set(_.VENUE, d.venue).set(_.TAGS, d.tags).set(_.MEETUPGROUP, d.refs.meetup.map(_.group)).set(_.MEETUPEVENT, d.refs.meetup.map(_.event)).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, event))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateDescription(event: Event.Id)(description: LiquidMarkdown[Message.EventInfo]): Query.Update = {
    val q1 = table.update(fr0"description=$description").where(fr0"e.id=$event")
    val q2 = EVENTS.update.set(_.DESCRIPTION, description).where(_.ID.is(event))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateNotes(group: Group.Id, event: Event.Slug)(notes: String, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"orga_notes=$notes, orga_notes_updated_at=$now, orga_notes_updated_by=$by").where(where(group, event))
    val q2 = EVENTS.update.set(_.ORGA_NOTES, notes).set(_.ORGA_NOTES_UPDATED_AT, now).set(_.ORGA_NOTES_UPDATED_BY, by).where(where2(group, event))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"cfp_id=$cfp, updated_at=$now, updated_by=$by").where(where(group, event))
    val q2 = EVENTS.update.setOpt(_.CFP_ID, cfp).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, event))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateTalks(group: Group.Id, event: Event.Slug)(talks: List[Proposal.Id], by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"talks=$talks, updated_at=$now, updated_by=$by").where(where(group, event))
    val q2 = EVENTS.update.set(_.TALKS, talks).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, event))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updatePublished(group: Group.Id, event: Event.Slug)(by: User.Id, now: Instant): Query.Update = {
    val q1 = table.update(fr0"published=$now, updated_at=$now, updated_by=$by").where(where(group, event))
    val q2 = EVENTS.update.setOpt(_.PUBLISHED, now).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where2(group, event))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(event: Event.Id): Query.Select[Event] = {
    val q1 = table.select[Event].where(fr0"e.id=$event").one
    val q2 = EVENTS.select.where(_.ID.is(event)).option[Event](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOne(group: Group.Id, event: Event.Slug): Query.Select[Event] = {
    val q1 = table.select[Event].where(where(group, event)).one
    val q2 = EVENTS.select.where(where2(group, event)).option[Event](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneFull(group: Group.Id, event: Event.Slug): Query.Select[Event.Full] = {
    val q1 = tableFull.select[Event.Full].where(where(group, event)).one
    val q2 = EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(group) and EVENTS.SLUG.is(event)).option[Event.Full](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneFull(group: Group.Slug, event: Event.Slug)(implicit ctx: UserAwareCtx): Query.Select[Event.Full] = {
    val q1 = tableFull.select[Event.Full].where(fr0"g.slug=$group AND e.slug=$event AND (e.published IS NOT NULL OR g.owners LIKE ${"%" + ctx.user.map(_.id).getOrElse("unknown") + "%"})").one
    val q2 = EVENTS_FULL.select.where(GROUPS.SLUG.is(group) and EVENTS.SLUG.is(event) and (EVENTS.PUBLISHED.notNull or GROUPS.OWNERS.like("%" + ctx.user.map(_.id).getOrElse("unknown") + "%")).par).option[Event.Full](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOnePublished(group: Group.Id, event: Event.Slug): Query.Select[Event.Full] = {
    val q1 = tableFull.select[Event.Full].where(fr0"e.group_id=$group AND e.slug=$event AND e.published IS NOT NULL").one
    val q2 = EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(group) and EVENTS.SLUG.is(event) and EVENTS.PUBLISHED.notNull).option[Event.Full](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Event] = {
    val q1 = table.selectPage[Event](params, adapt(ctx)).where(fr0"e.group_id=${ctx.group.id}")
    val q2 = EVENTS.select.where(_.GROUP_ID.is(ctx.group.id)).page[Event](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Event.Full] = {
    val q1 = tableFull.selectPage[Event.Full](params, adapt(ctx)).where(fr0"e.group_id=${ctx.group.id}")
    val q2 = EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(ctx.group.id)).page[Event.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllPublishedSlugs()(implicit ctx: UserAwareCtx): Query.Select[(Group.Id, Event.Slug)] = {
    val q1 = table.select[(Group.Id, Event.Slug)].fields(Field("group_id", "e"), Field("slug", "e")).where(fr0"e.published IS NOT NULL")
    val q2 = EVENTS.select.withFields(_.GROUP_ID, _.SLUG).where(_.PUBLISHED.notNull).all[(Group.Id, Event.Slug)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPagePublished(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Event.Full] = {
    val q1 = tableFull.selectPage[Event.Full](params, adapt(ctx)).where(fr0"e.group_id=$group AND e.published IS NOT NULL")
    val q2 = EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(group) and EVENTS.PUBLISHED.notNull).page[Event.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(ids: NonEmptyList[Event.Id]): Query.Select[Event] = {
    val q1 = table.select[Event].where(Fragments.in(fr"e.id", ids))
    val q2 = EVENTS.select.where(_.ID.in(ids)).all[Event]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllFromGroups(groups: NonEmptyList[Group.Id])(implicit ctx: AdminCtx): Query.Select[Event] = {
    val q1 = table.select[Event].where(Fragments.in(fr"e.group_id", groups))
    val q2 = EVENTS.select.where(_.GROUP_ID.in(groups)).all[Event]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(group: Group.Id, venue: Venue.Id): Query.Select[Event] = {
    val q1 = table.select[Event].where(fr0"e.group_id=$group AND e.venue=$venue")
    val q2 = EVENTS.select.where(e => e.GROUP_ID.is(group) and e.VENUE.is(venue)).all[Event]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAll(group: Group.Id, partner: Partner.Id): Query.Select[(Event, Venue)] = {
    val q1 = tableWithVenue.select[(Event, Venue)].where(fr0"e.group_id=$group AND v.partner_id=$partner")
    val q2 = EVENTS_WITH_VENUES.select.where(EVENTS.GROUP_ID.is(group) and VENUES.PARTNER_ID.is(partner)).all[(Event, Venue)]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageAfterFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.SelectPage[Event.Full] = {
    val q1 = tableFull.selectPage[Event.Full](params, adapt(ctx)).where(fr0"e.group_id=${ctx.group.id} AND e.start > ${ctx.now.truncatedTo(ChronoUnit.DAYS)}")
    val date = TimeUtils.toLocalDateTime(ctx.now.truncatedTo(ChronoUnit.DAYS))
    val q2 = EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(ctx.group.id) and EVENTS.START.gt(date)).page[Event.Full](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectPageIncoming(params: Page.Params)(implicit ctx: UserCtx): Query.SelectPage[(Event.Full, Option[Event.Rsvp])] = {
    val q1 = tableFullWithMemberAndRsvp.selectPage[(Event.Full, Option[Event.Rsvp])](params, adapt(ctx)).where(fr0"e.start > ${ctx.now} AND e.published IS NOT NULL AND gm.user_id=${ctx.user.id}")
    val date = TimeUtils.toLocalDateTime(ctx.now)
    val q2 = EVENTS_FULL_WITH_MEMBERS_AND_RSVPS.select.where(EVENTS.START.gt(date) and EVENTS.PUBLISHED.notNull and GROUP_MEMBERS.USER_ID.is(ctx.user.id)).page[(Event.Full, Option[Event.Rsvp])](params, ctx.toDb)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectTags(): Query.Select[List[Tag]] = {
    val q1 = table.select[List[Tag]].fields(Field("tags", "e"))
    val q2 = EVENTS.select.withFields(_.TAGS).all[List[Tag]]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def where(group: Group.Id, event: Event.Slug): Fragment = fr0"e.group_id=$group AND e.slug=$event"

  private def where2(group: Group.Id, event: Event.Slug): Cond = EVENTS.GROUP_ID.is(group) and EVENTS.SLUG.is(event)

  private[sql] def countRsvp(event: Event.Id, answer: Event.Rsvp.Answer): Query.Select[Long] = {
    val q1 = rsvpTable.select[Long].fields(Field("COUNT(*)", "")).where(fr0"er.event_id=$event AND er.answer=$answer GROUP BY er.event_id, er.answer").sort(Table.Sort("event_id", "er")).one
    val q2 = EVENT_RSVPS.select.fields(AggField("COUNT(*)")).where(er => er.EVENT_ID.is(event) and er.ANSWER.is(answer)).groupBy(EVENT_RSVPS.EVENT_ID, EVENT_RSVPS.ANSWER).orderBy(EVENT_RSVPS.EVENT_ID.asc).option[Long](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def insertRsvp(e: Event.Rsvp): Query.Insert[Event.Rsvp] = {
    val q1 = rsvpTable.insert[Event.Rsvp](e, _ => fr0"${e.event}, ${e.user.id}, ${e.answer}, ${e.answeredAt}")
    val q2 = EVENT_RSVPS.insert.values(e.event, e.user.id, e.answer, e.answeredAt)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def updateRsvp(event: Event.Id, user: User.Id, answer: Answer, now: Instant): Query.Update = {
    val q1 = rsvpTable.update(fr0"answer=$answer, answered_at=$now").where(fr0"er.event_id=$event AND er.user_id=$user")
    val q2 = EVENT_RSVPS.update.set(_.ANSWER, answer).set(_.ANSWERED_AT, now).where(er => er.EVENT_ID.is(event) and er.USER_ID.is(user))
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllRsvp(event: Event.Id): Query.Select[Event.Rsvp] = {
    val q1 = rsvpTableWithUser.select[Event.Rsvp].where(fr0"er.event_id=$event")
    val q2 = RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event)).all[Event.Rsvp]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectAllRsvp(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): Query.Select[Event.Rsvp] = {
    val q1 = rsvpTableWithUser.select[Event.Rsvp].where(fr0"er.event_id=$event AND " ++ Fragments.in(fr"er.answer", answers))
    val q2 = RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event) and EVENT_RSVPS.ANSWER.in(answers)).all[Event.Rsvp]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectOneRsvp(event: Event.Id, user: User.Id): Query.Select[Event.Rsvp] = {
    val q1 = rsvpTableWithUser.select[Event.Rsvp].where(fr0"er.event_id=$event AND er.user_id=$user")
    val q2 = RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event) and EVENT_RSVPS.USER_ID.is(user)).option[Event.Rsvp]
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private[sql] def selectFirstRsvp(event: Event.Id, answer: Answer): Query.Select[Event.Rsvp] = {
    val q1 = rsvpTableWithUser.select[Event.Rsvp].where(fr0"er.event_id=$event AND er.answer=$answer").sort(Table.Sort("answer", Field("answered_at", "er"))).one
    val q2 = RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event) and EVENT_RSVPS.ANSWER.is(answer)).orderBy(EVENT_RSVPS.ANSWERED_AT.asc).option[Event.Rsvp](limit = true)
    GenericRepo.assertEqual(q1.fr, q2.fr)
    q1
  }

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
