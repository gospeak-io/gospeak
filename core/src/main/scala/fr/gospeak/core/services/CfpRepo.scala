package fr.gospeak.core.services

import java.time.Instant

import cats.effect.IO
import fr.gospeak.core.domain.{Cfp, Group, Talk, User}
import fr.gospeak.libs.scalautils.domain.Page

trait CfpRepo {
  def create(group: Group.Id, data: Cfp.Data, by: User.Id, now: Instant): IO[Cfp]

  def find(slug: Cfp.Slug): IO[Option[Cfp]]

  def find(id: Cfp.Id): IO[Option[Cfp]]

  def find(id: Group.Id): IO[Option[Cfp]]

  def listAvailables(talk: Talk.Id, params: Page.Params): IO[Page[Cfp]]
}
