package gospeak.infra.services.storage.sql.utils

import doobie.syntax.string._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.Info
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.libs.scala.domain.GMapPlace

object GenericQuery {
  private val _ = gMapPlaceMeta // for intellij not remove DoobieMappings import

  def insertLocation(p: Option[GMapPlace]): Fragment =
    fr0"$p, ${p.map(_.id)}, ${p.map(_.geo.lat)}, ${p.map(_.geo.lng)}, ${p.flatMap(_.locality)}, ${p.map(_.country)}"

  def updateLocation(p: Option[GMapPlace]): Fragment =
    fr0"location=$p, location_id=${p.map(_.id)}, location_lat=${p.map(_.geo.lat)}, location_lng=${p.map(_.geo.lng)}, location_locality=${p.flatMap(_.locality)}, location_country=${p.map(_.country)}"

  def insertInfo(i: Info): Fragment =
    fr0"${i.createdAt}, ${i.createdBy}, ${i.updatedAt}, ${i.updatedBy}"
}
