package gospeak.web.domain

import gospeak.core.domain._
import gospeak.core.domain.utils.Constants
import gospeak.web.pages.published.cfps.routes.CfpCtrl
import gospeak.web.pages.published.events.routes.EventCtrl
import gospeak.web.pages.published.groups.routes.GroupCtrl
import gospeak.web.pages.published.speakers.routes.SpeakerCtrl
import gospeak.web.pages.published.videos.routes.VideoCtrl
import gospeak.web.utils.UserAwareReq
import play.api.mvc.AnyContent

final case class Shareable(url: String,
                           text: String,
                           owners: List[Shareable.Owner]) {
  def tweet: String = {
    val by = if (owners.nonEmpty) s" by ${owners.map(_.twitterName).mkString(" and ")}" else ""
    val via = s"via ${Constants.Gospeak.twitter.handle}"
    s"$text$by $url $via"
  }
}

object Shareable {
  def apply(u: User)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(SpeakerCtrl.detail(u.slug)),
    text = u.name.value,
    owners = List())

  def apply(u: User, t: Talk, users: Seq[User])(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(SpeakerCtrl.talk(u.slug, t.slug)),
    text = s"Talk ${t.title.value}",
    owners = t.speakerUsers(users).map(Owner(_)))

  def apply(g: Group)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(GroupCtrl.detail(g.slug)),
    text = g.name.value + g.social.twitter.map(t => s" (${t.handle})").getOrElse(""),
    owners = List())

  def apply(g: Group, c: Cfp)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(CfpCtrl.detail(c.slug)),
    text = s"CFP '${c.name.value}'",
    owners = List(Owner(g)))

  def apply(e: ExternalEvent, c: ExternalCfp)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(CfpCtrl.detailExt(c.id)),
    text = s"CFP for ${e.name.value}",
    owners = List())

  def apply(g: Group, e: Event)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(GroupCtrl.event(g.slug, e.slug)),
    text = e.name.value,
    owners = List(Owner(g)))

  def apply(e: ExternalEvent)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(EventCtrl.detailExt(e.id)),
    text = e.name.value,
    owners = List())

  def apply(g: Group, p: Proposal, users: Seq[User])(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(GroupCtrl.talk(g.slug, p.id)),
    text = s"Presentation of ${p.title.value}",
    owners = p.speakerUsers(users).map(Owner(_)))

  def apply(e: ExternalEvent, p: ExternalProposal, users: Seq[User])(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(EventCtrl.proposalExt(e.id, p.id)),
    text = s"Presentation of ${p.title.value}",
    owners = p.speakerUsers(users).map(Owner(_)))

  def apply(v: Video)(implicit req: UserAwareReq[AnyContent]): Shareable = new Shareable(
    url = req.toAbsolute(VideoCtrl.detail(v.id)),
    text = v.title,
    owners = List())

  final case class Owner(name: String, twitterHandle: Option[String]) {
    def twitterName: String = twitterHandle.getOrElse(name)
  }

  object Owner {
    def apply(u: User): Owner = new Owner(
      name = u.name.value,
      twitterHandle = u.social.twitter.map(_.handle))

    def apply(g: Group): Owner = new Owner(
      name = g.name.value,
      twitterHandle = g.social.twitter.map(_.handle))

    def apply(e: ExternalEvent): Owner = new Owner(
      name = e.name.value,
      twitterHandle = e.twitterAccount.map(_.handle))
  }

}
