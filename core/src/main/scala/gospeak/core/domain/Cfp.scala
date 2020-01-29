package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

final case class Cfp(id: Cfp.Id,
                     group: Group.Id,
                     slug: Cfp.Slug,
                     name: Cfp.Name,
                     begin: Option[LocalDateTime],
                     close: Option[LocalDateTime],
                     description: Markdown,
                     tags: Seq[Tag],
                     info: Info) {
  def data: Cfp.Data = Cfp.Data(this)

  def users: Seq[User.Id] = info.users

  def isActive(now: LocalDateTime): Boolean = begin.forall(_.isBefore(now)) && close.forall(_.isAfter(now))
}

object Cfp {
  def apply(group: Group.Id, data: Data, info: Info): Cfp =
    new Cfp(Id.generate(), group, data.slug, data.name, data.begin, data.close, data.description, data.tags, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Cfp.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Cfp.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Cfp.Slug,
                        name: Cfp.Name,
                        begin: Option[LocalDateTime],
                        close: Option[LocalDateTime],
                        description: Markdown,
                        tags: Seq[Tag])

  object Data {
    def apply(cfp: Cfp): Data = new Data(cfp.slug, cfp.name, cfp.begin, cfp.close, cfp.description, cfp.tags)
  }

}
