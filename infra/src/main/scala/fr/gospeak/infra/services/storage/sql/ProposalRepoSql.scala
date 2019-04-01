package fr.gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.core.services.ProposalRepo
import fr.gospeak.infra.services.storage.sql.tables.ProposalTable
import fr.gospeak.infra.services.storage.sql.utils.GenericRepo
import fr.gospeak.infra.utils.DoobieUtils.Queries
import fr.gospeak.libs.scalautils.domain.{Done, Page, Slides, Video}

class ProposalRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with ProposalRepo {
  override def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal] =
    run(ProposalTable.insert, Proposal(Proposal.Id.generate(), talk, cfp, None, data.title, data.duration, Proposal.Status.Pending, data.description, speakers, data.slides, data.video, Info(by, now)))

  override def updateStatus(id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): IO[Done] =
    run(ProposalTable.updateStatus(id)(status, event))

  override def updateSlides(id: Proposal.Id)(slides: Slides, now: Instant, user: User.Id): IO[Done] = run(ProposalTable.updateSlides(id)(slides, now, user))

  override def updateVideo(id: Proposal.Id)(video: Video, now: Instant, user: User.Id): IO[Done] = run(ProposalTable.updateVideo(id)(video, now, user))

  override def find(id: Proposal.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(id).option)

  override def find(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]] = run(ProposalTable.selectOne(talk, cfp).option)

  override def list(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]] = run(Queries.selectPage(ProposalTable.selectPage(talk, _), params))

  override def list(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(ProposalTable.selectPage(cfp, _), params))

  override def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]] = run(Queries.selectPage(ProposalTable.selectPage(cfp, status, _), params))

  override def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]] = runIn(ProposalTable.selectAll)(ids)
}
