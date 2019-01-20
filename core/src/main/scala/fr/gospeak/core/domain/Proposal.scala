package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, Info, UuidIdBuilder}

case class Proposal(id: Proposal.Id,
                    talk: Talk.Id,
                    group: Group.Id,
                    title: Proposal.Title,
                    description: String,
                    info: Info)

object Proposal {

  class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Proposal.Id]("Proposal.Id", new Proposal.Id(_))

  case class Title(value: String) extends AnyVal

}
