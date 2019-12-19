package gospeak.core.domain

import gospeak.libs.scala.domain.{DataClass, IId, Markdown, UuidIdBuilder}

final case class Performance(id: Performance.Id,
                             venue: String,
                             title: String,
                             description: Markdown)

object Performance {

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Performance.Id", new Id(_))

  final case class Data(venue: String,
                        title: String,
                        description: Markdown)

  object Data {
    def apply(performance: Performance): Data = new Data(performance.venue, performance.title, performance.description)
  }

}
