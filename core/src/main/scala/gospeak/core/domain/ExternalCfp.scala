package gospeak.core.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.Info
import gospeak.libs.scala.domain._

case class ExternalCfp(id: ExternalCfp.Id,
                       event: ExternalEvent.Id,
                       description: Markdown,
                       begin: Option[LocalDateTime],
                       close: Option[LocalDateTime],
                       url: Url,
                       info: Info) {
  def data: ExternalCfp.Data = ExternalCfp.Data(this)

  def isActive(now: LocalDateTime): Boolean = begin.forall(_.isBefore(now)) && close.forall(_.isAfter(now))
}

object ExternalCfp {
  def apply(d: Data, event: ExternalEvent.Id, info: Info): ExternalCfp =
    new ExternalCfp(Id.generate(), event, d.description, d.begin, d.close, d.url, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("ExternalCfp.Id", new Id(_))

  final case class Full(cfp: ExternalCfp, event: ExternalEvent) {
    def id: Id = cfp.id

    def description: Markdown = cfp.description

    def begin: Option[LocalDateTime] = cfp.begin

    def close: Option[LocalDateTime] = cfp.close

    def url: Url = cfp.url

    def data: Data = cfp.data

    def isActive(now: LocalDateTime): Boolean = cfp.isActive(now)
  }

  final case class Data(description: Markdown,
                        begin: Option[LocalDateTime],
                        close: Option[LocalDateTime],
                        url: Url)

  object Data {
    def apply(c: ExternalCfp): Data = new Data(c.description, c.begin, c.close, c.url)
  }

  final case class DuplicateParams(cfpUrl: Option[String],
                                   cfpName: Option[String],
                                   cfpEndDate: Option[LocalDateTime],
                                   eventUrl: Option[String],
                                   eventStartDate: Option[LocalDateTime],
                                   twitterAccount: Option[String],
                                   twitterHashtag: Option[String])

  object DuplicateParams {
    val defaults: DuplicateParams = DuplicateParams(None, None, None, None, None, None, None)
  }

}
