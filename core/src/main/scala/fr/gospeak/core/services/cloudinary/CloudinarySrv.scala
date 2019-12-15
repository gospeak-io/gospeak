package fr.gospeak.core.services.cloudinary

import cats.effect.IO
import fr.gospeak.core.domain.{Group, Partner, User}
import fr.gospeak.libs.scalautils.domain.{Avatar, Banner, Logo}

trait CloudinarySrv {
  def signRequest(params: Map[String, String]): Either[String, String]

  def uploadAvatar(user: User): IO[Avatar]

  def uploadExternalCfpLogo(logo: Logo): IO[Logo]

  def uploadGroupLogo(group: Group, logo: Logo): IO[Logo]

  def uploadGroupBanner(group: Group, banner: Banner): IO[Banner]

  def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo]
}
