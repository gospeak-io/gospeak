package gospeak.web.domain

import play.api.mvc.Call

final case class Breadcrumb(links: Seq[Breadcrumb.Link]) {
  def add(l: (String, Call)*): Breadcrumb = copy(links = links ++ l.map { case (name, link) => Breadcrumb.Link(name, Some(link)) })

  def addOpt(l: (String, Option[Call])*): Breadcrumb = copy(links = links ++ l.map { case (name, link) => Breadcrumb.Link(name, link) })
}

object Breadcrumb {

  final case class Link(name: String, link: Option[Call])

  def apply(link: (String, Call)): Breadcrumb =
    new Breadcrumb(Seq(Link(link._1, Some(link._2))))
}
