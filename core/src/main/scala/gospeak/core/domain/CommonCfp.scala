package gospeak.core.domain

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}

import gospeak.core.domain.utils.Constants
import gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

final case class CommonCfp(name: String,
                           logo: Option[Logo],
                           begin: Option[LocalDateTime],
                           close: Option[LocalDateTime],
                           location: Option[GMapPlace],
                           description: Markdown,
                           tags: List[Tag],
                           extra: Either[CommonCfp.External, CommonCfp.Internal]) {
  def closesInDays(nb: Int, now: Instant): Boolean = close.exists(_.toInstant(Constants.defaultZoneId).isBefore(now.minus(nb, ChronoUnit.DAYS)))

  def fold[A](f: CommonCfp.External => A)(g: CommonCfp.Internal => A): A = extra.fold(f, g)

  def internal: Option[CommonCfp.Internal] = extra.right.toOption

  def external: Option[CommonCfp.External] = extra.left.toOption
}

object CommonCfp {
  def apply(group: Group, cfp: Cfp): CommonCfp = new CommonCfp(
    name = cfp.name.value,
    logo = group.logo,
    begin = cfp.begin,
    close = cfp.close,
    location = group.location,
    description = cfp.description,
    tags = cfp.tags,
    extra = Right(Internal(
      slug = cfp.slug,
      group = InternalGroup(
        id = group.id,
        slug = group.slug))))

  def apply(cfp: ExternalCfp.Full): CommonCfp = new CommonCfp(
    name = cfp.event.name.value,
    logo = cfp.event.logo,
    begin = cfp.begin,
    close = cfp.close,
    location = cfp.event.location,
    description = cfp.description,
    tags = cfp.event.tags,
    extra = Left(External(
      id = cfp.id,
      url = cfp.url,
      event = ExternalExternalEvent(
        start = cfp.event.start,
        finish = cfp.event.finish,
        url = cfp.event.url,
        tickets = cfp.event.tickets,
        videos = cfp.event.videos,
        twitterAccount = cfp.event.twitterAccount,
        twitterHashtag = cfp.event.twitterHashtag))))

  final case class InternalGroup(id: Group.Id,
                                 slug: Group.Slug)

  final case class Internal(slug: Cfp.Slug,
                            group: InternalGroup)

  final case class ExternalExternalEvent(start: Option[LocalDateTime],
                                         finish: Option[LocalDateTime],
                                         url: Option[Url],
                                         tickets: Option[Url],
                                         videos: Option[Url],
                                         twitterAccount: Option[TwitterAccount],
                                         twitterHashtag: Option[TwitterHashtag])

  final case class External(id: ExternalCfp.Id,
                            url: Url,
                            event: ExternalExternalEvent)

}
