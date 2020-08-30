package gospeak.web.domain

import play.api.mvc.Call

final case class Breadcrumb(links: List[Breadcrumb.Link]) {
  def add(name: String, link: Call, showLink: Boolean): Breadcrumb = copy(links = links :+ Breadcrumb.Link(name, link, showLink))

  def add(l: (String, Call)*): Breadcrumb = copy(links = links ++ l.map { case (name, link) => Breadcrumb.Link(name, link, showLink = true) })
}

object Breadcrumb {

  final case class Link(name: String, link: Call, showLink: Boolean)

  def apply(name: String, link: Call): Breadcrumb =
    new Breadcrumb(List(Link(name, link, showLink = true)))
}
