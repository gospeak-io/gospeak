package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, Markdown, UuidIdBuilder}

final case class Proposal(id: Proposal.Id,
                          talk: Talk.Id,
                          cfp: Cfp.Id,
                          title: Talk.Title,
                          description: Markdown,
                          info: Info)

object Proposal {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Proposal.Id", new Id(_))

}
