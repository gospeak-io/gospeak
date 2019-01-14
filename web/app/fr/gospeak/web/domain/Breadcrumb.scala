package fr.gospeak.web.domain

import play.api.mvc.Call

case class Breadcrumb(links: Seq[BreadcrumbLink]) {
  def add(l: (String, Call)*): Breadcrumb =
    copy(links = links ++ l.map { case (name, link) => BreadcrumbLink(name, link) })
}

case class BreadcrumbLink(name: String, link: Call)
