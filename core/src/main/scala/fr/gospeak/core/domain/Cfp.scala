package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, SlugBuilder, UuidIdBuilder}

final case class Cfp(id: Cfp.Id,
                     slug: Cfp.Slug,
                     name: Cfp.Name,
                     description: String,
                     group: Group.Id,
                     info: Info)

object Cfp {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Cfp.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Cfp.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

}
