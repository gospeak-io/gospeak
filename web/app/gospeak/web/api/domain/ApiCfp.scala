package gospeak.web.api.domain

import java.time.LocalDateTime

import gospeak.core.domain.utils.{BasicCtx, OrgaCtx}
import gospeak.core.domain.{Cfp, CommonCfp, Group, User}
import gospeak.web.api.domain.utils.{ApiInfo, ApiPlace}
import play.api.libs.json.{Json, Writes}

object ApiCfp {

  // data to display for orgas (everything)
  final case class Orga(slug: String,
                        name: String,
                        begin: Option[LocalDateTime],
                        close: Option[LocalDateTime],
                        description: String,
                        tags: Seq[String],
                        info: ApiInfo)

  object Orga {
    implicit val writes: Writes[Orga] = Json.writes[Orga]
  }

  def orga(cfp: Cfp, users: Seq[User])(implicit ctx: OrgaCtx): Orga =
    new Orga(
      slug = cfp.slug.value,
      name = cfp.name.value,
      begin = cfp.begin,
      close = cfp.close,
      description = cfp.description.value,
      tags = cfp.tags.map(_.value),
      info = ApiInfo.from(cfp.info, users))

  // data to display publicly
  final case class Published(kind: String,
                             ref: String,
                             name: String,
                             logo: Option[String],
                             url: Option[String],
                             begin: Option[LocalDateTime],
                             close: Option[LocalDateTime],
                             location: Option[ApiPlace],
                             description: String,
                             eventStart: Option[LocalDateTime],
                             eventFinish: Option[LocalDateTime],
                             eventUrl: Option[String],
                             eventTickets: Option[String],
                             eventVideos: Option[String],
                             twitterAccount: Option[String],
                             twitterHashtag: Option[String],
                             tags: Seq[String],
                             group: Option[ApiGroup.Embed])

  object Published {
    implicit val writes: Writes[Published] = Json.writes[Published]
  }

  def published(cfp: CommonCfp, groups: Seq[Group])(implicit ctx: BasicCtx): Published =
    new Published(
      kind = if (cfp.slug.isDefined) "internal" else "external",
      ref = cfp.slug.map(_.value).orElse(cfp.id.map(_.value)).getOrElse("fail-ref"),
      name = cfp.name,
      logo = cfp.logo.map(_.value),
      url = cfp.url.map(_.value),
      begin = cfp.begin,
      close = cfp.close,
      location = cfp.location.map(ApiPlace.from),
      description = cfp.description.value,
      eventStart = cfp.eventStart,
      eventFinish = cfp.eventFinish,
      tags = cfp.tags.map(_.value),
      eventUrl = cfp.eventUrl.map(_.value),
      eventTickets = cfp.eventTickets.map(_.value),
      eventVideos = cfp.eventVideos.map(_.value),
      twitterAccount = cfp.twitterAccount.map(_.url.value),
      twitterHashtag = cfp.twitterHashtag.map(_.value),
      group = cfp.group.flatMap { case (id, _) => groups.find(_.id == id) }.map(ApiGroup.embed))

  // embedded data in other models, should be public
  final case class Embed(slug: String,
                         name: String,
                         begin: Option[LocalDateTime],
                         close: Option[LocalDateTime])

  object Embed {
    implicit val writes: Writes[Embed] = Json.writes[Embed]
  }

  def embed(cfp: Cfp)(implicit ctx: BasicCtx): Embed =
    new Embed(
      slug = cfp.slug.value,
      name = cfp.name.value,
      begin = cfp.begin,
      close = cfp.close)

}
