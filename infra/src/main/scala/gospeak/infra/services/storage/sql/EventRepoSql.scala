package gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain.Event.Rsvp.Answer
import gospeak.core.domain._
import gospeak.core.domain.messages.Message
import gospeak.core.domain.utils._
import gospeak.core.services.storage.EventRepo
import gospeak.infra.services.storage.sql.EventRepoSql._
import gospeak.infra.services.storage.sql.database.Tables._
import gospeak.infra.services.storage.sql.database.tables.{EVENTS, EVENT_RSVPS}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.TimeUtils
import gospeak.libs.scala.domain._
import gospeak.libs.sql.dsl.{AggField, Cond, Query}

class EventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with EventRepo {
  override def create(data: Event.Data)(implicit ctx: OrgaCtx): IO[Event] = {
    val event = Event.create(ctx.group.id, data, ctx.info)
    insert(event).run(xa).map(_ => event)
  }

  override def edit(event: Event.Slug, data: Event.Data)(implicit ctx: OrgaCtx): IO[Unit] = {
    if (data.slug != event) {
      find(data.slug).flatMap {
        case None => update(ctx.group.id, event)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      update(ctx.group.id, event)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def editDescription(event: Event.Id, description: LiquidMarkdown[Message.EventInfo])(implicit ctx: AdminCtx): IO[Unit] =
    updateDescription(event)(description).run(xa)

  override def editNotes(event: Event.Slug, notes: String)(implicit ctx: OrgaCtx): IO[Unit] =
    updateNotes(ctx.group.id, event)(notes, ctx.user.id, ctx.now).run(xa)

  override def attachCfp(event: Event.Slug, cfp: Cfp.Id)(implicit ctx: OrgaCtx): IO[Unit] =
    updateCfp(ctx.group.id, event)(cfp, ctx.user.id, ctx.now).run(xa)

  override def editTalks(event: Event.Slug, talks: List[Proposal.Id])(implicit ctx: OrgaCtx): IO[Unit] =
    updateTalks(ctx.group.id, event)(talks, ctx.user.id, ctx.now).run(xa)

  override def publish(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Unit] =
    updatePublished(ctx.group.id, event)(ctx.user.id, ctx.now).run(xa)

  override def find(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event]] = selectOne(ctx.group.id, event).run(xa)

  override def find(event: Event.Id)(implicit ctx: AdminCtx): IO[Option[Event]] = selectOne(event).run(xa)

  override def findFull(event: Event.Slug)(implicit ctx: OrgaCtx): IO[Option[Event.Full]] = selectOneFull(ctx.group.id, event).run(xa)

  override def findPublished(group: Group.Id, event: Event.Slug): IO[Option[Event.Full]] = selectOnePublished(group, event).run(xa)

  override def findFull(group: Group.Slug, event: Event.Slug)(implicit ctx: UserAwareCtx): IO[Option[Event.Full]] = selectOneFull(group, event).run(xa)

  override def list(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event]] = selectPage(params).run(xa)

  override def listFull(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]] = selectPageFull(params).run(xa)

  override def list(venue: Venue.Id)(implicit ctx: OrgaCtx): IO[List[Event]] = selectAll(ctx.group.id, venue).run(xa)

  override def list(partner: Partner.Id)(implicit ctx: OrgaCtx): IO[List[(Event, Venue)]] = selectAll(ctx.group.id, partner).run(xa)

  override def listAllPublishedSlugs()(implicit ctx: UserAwareCtx): IO[List[(Group.Id, Event.Slug)]] = selectAllPublishedSlugs().run(xa)

  override def listPublished(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Event.Full]] = selectPagePublished(group, params).run(xa)

  override def list(ids: List[Event.Id]): IO[List[Event]] = runNel(selectAll, ids)

  override def listAllFromGroups(groups: List[Group.Id])(implicit ctx: AdminCtx): IO[List[Event]] = runNel[Group.Id, Event](selectAllFromGroups(_), groups)

  override def listAfter(params: Page.Params)(implicit ctx: OrgaCtx): IO[Page[Event.Full]] = selectPageAfterFull(params).run(xa)

  override def listIncoming(params: Page.Params)(implicit ctx: UserCtx): IO[Page[(Event.Full, Option[Event.Rsvp])]] = selectPageIncoming(params).run(xa)

  override def countYesRsvp(event: Event.Id): IO[Long] = countRsvp(event, Event.Rsvp.Answer.Yes).run(xa).map(_.getOrElse(0))

  override def listRsvps(event: Event.Id): IO[List[Event.Rsvp]] = selectAllRsvp(event).run(xa)

  override def listRsvps(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): IO[List[Event.Rsvp]] = selectAllRsvp(event, answers).run(xa)

  override def findRsvp(event: Event.Id, user: User.Id): IO[Option[Event.Rsvp]] = selectOneRsvp(event, user).run(xa)

  override def findFirstWait(event: Event.Id): IO[Option[Event.Rsvp]] = selectFirstRsvp(event, Answer.Wait).run(xa)

  override def createRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Unit] = insertRsvp(Event.Rsvp(event, answer, now, user)).run(xa)

  override def editRsvp(event: Event.Id, answer: Event.Rsvp.Answer)(user: User, now: Instant): IO[Unit] = updateRsvp(event, user.id, answer, now).run(xa)

  override def listTags(): IO[List[Tag]] = selectTags().run(xa).map(_.flatten.distinct)
}

object EventRepoSql {
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

  private[sql] def insert(e: Event): Query.Insert[EVENTS] =
  // EVENTS.insert.values(e.id, e.group, e.cfp, e.slug, e.name, e.kind, e.start, e.maxAttendee, e.allowRsvp, e.description, e.orgaNotes.text, e.orgaNotes.updatedAt, e.orgaNotes.updatedBy, e.venue, e.talks, e.tags, e.published, e.refs.meetup.map(_.group), e.refs.meetup.map(_.event), e.info.createdAt, e.info.createdBy, e.info.updatedAt, e.info.updatedBy)
    EVENTS.insert.values(fr0"${e.id}, ${e.group}, ${e.cfp}, ${e.slug}, ${e.name}, ${e.kind}, ${e.start}, ${e.maxAttendee}, ${e.allowRsvp}, ${e.description}, ${e.orgaNotes.text}, ${e.orgaNotes.updatedAt}, ${e.orgaNotes.updatedBy}, ${e.venue}, ${e.talks}, ${e.tags}, ${e.published}, ${e.refs.meetup.map(_.group)}, ${e.refs.meetup.map(_.event)}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}")

  private[sql] def update(group: Group.Id, event: Event.Slug)(d: Event.Data, by: User.Id, now: Instant): Query.Update[EVENTS] =
    EVENTS.update.set(_.CFP_ID, d.cfp).set(_.SLUG, d.slug).set(_.NAME, d.name).set(_.KIND, d.kind).set(_.START, d.start).set(_.MAX_ATTENDEE, d.maxAttendee).set(_.ALLOW_RSVP, d.allowRsvp).set(_.DESCRIPTION, d.description).set(_.VENUE, d.venue).set(_.TAGS, d.tags).set(_.MEETUPGROUP, d.refs.meetup.map(_.group)).set(_.MEETUPEVENT, d.refs.meetup.map(_.event)).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, event))

  private[sql] def updateDescription(event: Event.Id)(description: LiquidMarkdown[Message.EventInfo]): Query.Update[EVENTS] =
    EVENTS.update.set(_.DESCRIPTION, description).where(_.ID.is(event))

  private[sql] def updateNotes(group: Group.Id, event: Event.Slug)(notes: String, by: User.Id, now: Instant): Query.Update[EVENTS] =
    EVENTS.update.set(_.ORGA_NOTES, notes).set(_.ORGA_NOTES_UPDATED_AT, now).set(_.ORGA_NOTES_UPDATED_BY, by).where(where(group, event))

  private[sql] def updateCfp(group: Group.Id, event: Event.Slug)(cfp: Cfp.Id, by: User.Id, now: Instant): Query.Update[EVENTS] =
    EVENTS.update.set(_.CFP_ID, cfp).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, event))

  private[sql] def updateTalks(group: Group.Id, event: Event.Slug)(talks: List[Proposal.Id], by: User.Id, now: Instant): Query.Update[EVENTS] =
    EVENTS.update.set(_.TALKS, talks).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, event))

  private[sql] def updatePublished(group: Group.Id, event: Event.Slug)(by: User.Id, now: Instant): Query.Update[EVENTS] =
    EVENTS.update.set(_.PUBLISHED, now).set(_.UPDATED_AT, now).set(_.UPDATED_BY, by).where(where(group, event))

  private[sql] def selectOne(event: Event.Id): Query.Select.Optional[Event] =
    EVENTS.select.where(_.ID.is(event)).option[Event](limit = true)

  private[sql] def selectOne(group: Group.Id, event: Event.Slug): Query.Select.Optional[Event] =
    EVENTS.select.where(where(group, event)).option[Event](limit = true)

  private[sql] def selectOneFull(group: Group.Id, event: Event.Slug): Query.Select.Optional[Event.Full] =
    EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(group) and EVENTS.SLUG.is(event)).option[Event.Full](limit = true)

  private[sql] def selectOneFull(group: Group.Slug, event: Event.Slug)(implicit ctx: UserAwareCtx): Query.Select.Optional[Event.Full] =
    EVENTS_FULL.select.where(GROUPS.SLUG.is(group) and EVENTS.SLUG.is(event) and (EVENTS.PUBLISHED.notNull or GROUPS.OWNERS.like("%" + ctx.user.map(_.id).getOrElse("unknown") + "%")).par).option[Event.Full](limit = true)

  private[sql] def selectOnePublished(group: Group.Id, event: Event.Slug): Query.Select.Optional[Event.Full] =
    EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(group) and EVENTS.SLUG.is(event) and EVENTS.PUBLISHED.notNull).option[Event.Full](limit = true)

  private[sql] def selectPage(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Event] =
    EVENTS.select.where(_.GROUP_ID.is(ctx.group.id)).page[Event](params, ctx.toDb)

  private[sql] def selectPageFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Event.Full] =
    EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(ctx.group.id)).page[Event.Full](params, ctx.toDb)

  private[sql] def selectAllPublishedSlugs()(implicit ctx: UserAwareCtx): Query.Select.All[(Group.Id, Event.Slug)] =
    EVENTS.select.withFields(_.GROUP_ID, _.SLUG).where(_.PUBLISHED.notNull).all[(Group.Id, Event.Slug)]

  private[sql] def selectPagePublished(group: Group.Id, params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[Event.Full] =
    EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(group) and EVENTS.PUBLISHED.notNull).page[Event.Full](params, ctx.toDb)

  private[sql] def selectAll(ids: NonEmptyList[Event.Id]): Query.Select.All[Event] =
    EVENTS.select.where(_.ID.in(ids)).all[Event]

  private[sql] def selectAllFromGroups(groups: NonEmptyList[Group.Id])(implicit ctx: AdminCtx): Query.Select.All[Event] =
    EVENTS.select.where(_.GROUP_ID.in(groups)).all[Event]

  private[sql] def selectAll(group: Group.Id, venue: Venue.Id): Query.Select.All[Event] =
    EVENTS.select.where(e => e.GROUP_ID.is(group) and e.VENUE.is(venue)).all[Event]

  private[sql] def selectAll(group: Group.Id, partner: Partner.Id): Query.Select.All[(Event, Venue)] =
    EVENTS_WITH_VENUES.select.where(EVENTS.GROUP_ID.is(group) and VENUES.PARTNER_ID.is(partner)).all[(Event, Venue)]

  private[sql] def selectPageAfterFull(params: Page.Params)(implicit ctx: OrgaCtx): Query.Select.Paginated[Event.Full] =
    EVENTS_FULL.select.where(EVENTS.GROUP_ID.is(ctx.group.id) and EVENTS.START.gt(TimeUtils.toLocalDateTime(ctx.now.truncatedTo(ChronoUnit.DAYS)))).page[Event.Full](params, ctx.toDb)

  private[sql] def selectPageIncoming(params: Page.Params)(implicit ctx: UserCtx): Query.Select.Paginated[(Event.Full, Option[Event.Rsvp])] =
    EVENTS_FULL_WITH_MEMBERS_AND_RSVPS.select.where(EVENTS.START.gt(TimeUtils.toLocalDateTime(ctx.now)) and EVENTS.PUBLISHED.notNull and GROUP_MEMBERS.USER_ID.is(ctx.user.id)).page[(Event.Full, Option[Event.Rsvp])](params, ctx.toDb)

  private[sql] def selectTags(): Query.Select.All[List[Tag]] =
    EVENTS.select.withFields(_.TAGS).all[List[Tag]]

  private def where(group: Group.Id, event: Event.Slug): Cond = EVENTS.GROUP_ID.is(group) and EVENTS.SLUG.is(event)

  private[sql] def countRsvp(event: Event.Id, answer: Event.Rsvp.Answer): Query.Select.Optional[Long] =
    EVENT_RSVPS.select.fields(AggField("COUNT(*)")).where(er => er.EVENT_ID.is(event) and er.ANSWER.is(answer)).groupBy(EVENT_RSVPS.EVENT_ID, EVENT_RSVPS.ANSWER).orderBy(EVENT_RSVPS.EVENT_ID.asc).option[Long](limit = true)

  private[sql] def insertRsvp(e: Event.Rsvp): Query.Insert[EVENT_RSVPS] =
    EVENT_RSVPS.insert.values(e.event, e.user.id, e.answer, e.answeredAt)

  private[sql] def updateRsvp(event: Event.Id, user: User.Id, answer: Answer, now: Instant): Query.Update[EVENT_RSVPS] =
    EVENT_RSVPS.update.set(_.ANSWER, answer).set(_.ANSWERED_AT, now).where(er => er.EVENT_ID.is(event) and er.USER_ID.is(user))

  private[sql] def selectAllRsvp(event: Event.Id): Query.Select.All[Event.Rsvp] =
    RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event)).all[Event.Rsvp]

  private[sql] def selectAllRsvp(event: Event.Id, answers: NonEmptyList[Event.Rsvp.Answer]): Query.Select.All[Event.Rsvp] =
    RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event) and EVENT_RSVPS.ANSWER.in(answers)).all[Event.Rsvp]

  private[sql] def selectOneRsvp(event: Event.Id, user: User.Id): Query.Select.Optional[Event.Rsvp] =
    RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event) and EVENT_RSVPS.USER_ID.is(user)).option[Event.Rsvp]

  private[sql] def selectFirstRsvp(event: Event.Id, answer: Answer): Query.Select.Optional[Event.Rsvp] =
    RSVP_WITH_USERS.select.where(EVENT_RSVPS.EVENT_ID.is(event) and EVENT_RSVPS.ANSWER.is(answer)).orderBy(EVENT_RSVPS.ANSWERED_AT.asc).option[Event.Rsvp](limit = true)
}
