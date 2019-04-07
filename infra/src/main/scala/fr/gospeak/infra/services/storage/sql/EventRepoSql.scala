package fr.gospeak.infra.services.storage.sql

import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.effect.IO
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.domain.{Event, Group, Proposal, User}
import fr.gospeak.core.services.EventRepo
import fr.gospeak.infra.services.storage.sql.tables.EventTable
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{CustomException, Done, Page}

class EventRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with EventRepo {
  override def create(group: Group.Id, data: Event.Data, by: User.Id, now: Instant): IO[Event] =
    run(EventTable.insert, Event(group, data, Info(by, now)))

  override def update(group: Group.Id, event: Event.Slug)(data: Event.Data, by: User.Id, now: Instant): IO[Done] = {
    if (data.slug != event) {
      find(group, data.slug).flatMap {
        case None => run(EventTable.update(group, event)(data, by, now))
        case _ => IO.raiseError(CustomException(s"You already have an event with slug ${data.slug}"))
      }
    } else {
      run(EventTable.update(group, event)(data, by, now))
    }
  }

  override def updateTalks(group: Group.Id, event: Event.Slug)(talks: Seq[Proposal.Id], by: User.Id, now: Instant): IO[Done] =
    run(EventTable.updateTalks(group, event)(talks, by, now))

  override def find(group: Group.Id, event: Event.Slug): IO[Option[Event]] = run(EventTable.selectOne(group, event).option)

  override def list(group: Group.Id, params: Page.Params): IO[Page[Event]] = run(Queries.selectPage(EventTable.selectPage(group, _), params))

  override def list(ids: Seq[Event.Id]): IO[Seq[Event]] = runIn[Event.Id, Event](EventTable.selectAll)(ids)

  override def listAfter(group: Group.Id, now: Instant, params: Page.Params): IO[Page[Event]] =
    run(Queries.selectPage(EventTable.selectAllAfter(group, now.truncatedTo(ChronoUnit.DAYS), _), params))
}
