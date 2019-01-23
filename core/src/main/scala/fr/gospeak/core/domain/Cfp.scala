package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, Info, UuidIdBuilder}

final case class Cfp(id: Cfp.Id,
                     slug: Cfp.Slug,
                     name: Cfp.Name,
                     description: String,
                     group: Group.Id,
                     info: Info)

object Cfp {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Cfp.Id]("Cfp.Id", new Cfp.Id(_))

  final case class Slug(value: String) extends AnyVal

  final case class Name(value: String) extends AnyVal

}
