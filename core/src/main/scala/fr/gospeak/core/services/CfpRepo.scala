package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain._
import fr.gospeak.libs.scalautils.domain.{Done, Page}

trait CfpRepo {
  def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp]

  def edit(group: Group.Id, cfp: Cfp.Slug)(data: Cfp.Data, by: User.Id, now: Instant): IO[Done]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def find(slug: Cfp.Slug): IO[Option[Cfp]]

  def find(group: Group.Id, slug: Cfp.Slug): IO[Option[Cfp]]

  def find(id: Event.Id): IO[Option[Cfp]]

  def list(group: Group.Id, params: Page.Params): IO[Page[Cfp]]

  def list(ids: Seq[Cfp.Id]): IO[Seq[Cfp]]

  def listAll(group: Group.Id): IO[Seq[Cfp]]

  def listAvailable(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]]
}
