package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, EnumBuilder, Markdown, UuidIdBuilder}

final case class Proposal(id: Proposal.Id,
                          talk: Talk.Id,
                          cfp: Cfp.Id,
                          event: Option[Event.Id],
                          title: Talk.Title,
                          status: Proposal.Status,
                          description: Markdown,
                          info: Info)

object Proposal {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Proposal.Id", new Id(_))

  sealed trait Status extends Product with Serializable

  object Status extends EnumBuilder[Status]("Proposal.Status") {

    case object Pending extends Status

    case object Accepted extends Status

    case object Rejected extends Status

    val all: Seq[Status] = Seq(Pending, Accepted, Rejected)
  }

}
