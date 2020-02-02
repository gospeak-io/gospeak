package gospeak.core.services.cloudinary

import cats.effect.IO
import gospeak.core.domain.{ExternalCfp, ExternalEvent, Group, Partner, User}
import gospeak.libs.scala.domain.{Avatar, Banner, Logo}

trait CloudinarySrv {
  def signRequest(params: Map[String, String]): Either[String, String]

  def uploadAvatar(user: User): IO[Avatar]

  def uploadGroupLogo(group: Group, logo: Logo): IO[Logo]

  def uploadGroupBanner(group: Group, banner: Banner): IO[Banner]

  def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo]

  def uploadExternalEventLogo(event: ExternalEvent, logo: Logo): IO[Logo]
}
