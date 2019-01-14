package fr.gospeak.web.domain

import play.api.mvc.Call

case class HeaderInfo(brand: NavLink,
                      links: Seq[NavMenu],
                      rightLinks: Seq[NavMenu]) {
  def activeFor(c: Call): HeaderInfo =
    copy(
      links = links.map(_.activeFor(c)),
      rightLinks = rightLinks.map(_.activeFor(c)))
}

sealed trait NavMenu {
  def activeFor(c: Call): NavMenu
}

case class NavLink(name: String, link: Call, active: Boolean = false) extends NavMenu {
  override def activeFor(c: Call): NavMenu =
    if (c == link) copy(active = true)
    else copy(active = false)
}

case class NavDropdown(name: String, links: Seq[NavLink]) extends NavMenu {
  override def activeFor(c: Call): NavMenu = this
}