package fr.gospeak.web.domain

import play.api.mvc.Call

final case class Breadcrumb(links: Seq[BreadcrumbLink]) {
  def add(l: (String, Call)*): Breadcrumb =
    copy(links = links ++ l.map { case (name, link) => BreadcrumbLink(name, link) })
}

object Breadcrumb {
  def apply(link: (String, Call)): Breadcrumb =
    new Breadcrumb(Seq(BreadcrumbLink(link._1, link._2)))
}

final case class BreadcrumbLink(name: String, link: Call)
