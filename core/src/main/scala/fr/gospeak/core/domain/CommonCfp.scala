package fr.gospeak.core.domain

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}

import fr.gospeak.core.domain.utils.Constants
import fr.gospeak.core.domain.utils.SocialAccounts.SocialAccount.TwitterAccount
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

// to hold Cfp or ExternalCfp in order to list them together
final case class CommonCfp(id: Option[ExternalCfp.Id],
                           slug: Option[Cfp.Slug],
                           name: String,
                           logo: Option[Logo],
                           url: Option[Url],
                           begin: Option[LocalDateTime],
                           close: Option[LocalDateTime],
                           location: Option[GMapPlace],
                           description: Markdown,
                           eventStart: Option[LocalDateTime],
                           eventFinish: Option[LocalDateTime],
                           eventUrl: Option[Url],
                           eventTickets: Option[Url],
                           eventVideos: Option[Url],
                           twitterAccount: Option[TwitterAccount],
                           twitterHashtag: Option[TwitterHashtag],
                           tags: Seq[Tag],
                           group: Option[(Group.Id, Group.Slug)]) {
  def closesInDays(nb: Int, now: Instant): Boolean = close.exists(_.toInstant(Constants.defaultZoneId).isBefore(now.minus(nb, ChronoUnit.DAYS)))

  def ref: Either[ExternalCfp.Id, Cfp.Slug] = slug.toEither(id.get)
}

object CommonCfp {
  def apply(group: Group, cfp: Cfp): CommonCfp = new CommonCfp(
    id = None,
    slug = Some(cfp.slug),
    name = cfp.name.value,
    logo = group.logo,
    url = None,
    begin = cfp.begin,
    close = cfp.close,
    location = group.location,
    description = cfp.description,
    eventStart = None,
    eventFinish = None,
    eventUrl = None,
    eventTickets = None,
    eventVideos = None,
    twitterAccount = None,
    twitterHashtag = None,
    tags = cfp.tags,
    group = Some(group.id -> group.slug))

  def apply(cfp: ExternalCfp): CommonCfp = new CommonCfp(
    id = Some(cfp.id),
    slug = None,
    name = cfp.name.value,
    logo = cfp.logo,
    url = Some(cfp.url),
    begin = cfp.begin,
    close = cfp.close,
    location = cfp.event.location,
    description = cfp.description,
    eventStart = cfp.event.start,
    eventFinish = cfp.event.finish,
    eventUrl = cfp.event.url,
    eventTickets = cfp.event.tickets,
    eventVideos = cfp.event.videos,
    twitterAccount = cfp.event.twitterAccount,
    twitterHashtag = cfp.event.twitterHashtag,
    tags = cfp.tags,
    group = None)
}
