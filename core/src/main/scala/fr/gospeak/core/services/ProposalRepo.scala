package fr.gospeak.core.services

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, Page, Slides, Video}

trait ProposalRepo {
  def create(talk: Talk.Id, cfp: Cfp.Id, data: Proposal.Data, speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): IO[Proposal]

  def editStatus(id: Proposal.Id)(status: Proposal.Status, event: Option[Event.Id]): IO[Done]

  def editSlides(id: Proposal.Id)(slides: Slides, now: Instant, user: User.Id): IO[Done]

  def editVideo(id: Proposal.Id)(video: Video, now: Instant, user: User.Id): IO[Done]

  def find(id: Proposal.Id): IO[Option[Proposal]]

  def find(talk: Talk.Id, cfp: Cfp.Id): IO[Option[Proposal]]

  def list(talk: Talk.Id, params: Page.Params): IO[Page[(Cfp, Proposal)]]

  def list(cfp: Cfp.Id, params: Page.Params): IO[Page[Proposal]]

  def list(cfp: Cfp.Id, status: Proposal.Status, params: Page.Params): IO[Page[Proposal]]

  def list(ids: Seq[Proposal.Id]): IO[Seq[Proposal]]
}
