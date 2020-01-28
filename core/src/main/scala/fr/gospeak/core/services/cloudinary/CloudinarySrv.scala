package fr.gospeak.core.services.cloudinary

import cats.effect.IO
import fr.gospeak.core.domain.{ExternalCfp, Group, Partner, User}
import gospeak.libs.scala.domain.{Avatar, Banner, Logo}

trait CloudinarySrv {
  def signRequest(params: Map[String, String]): Either[String, String]

  def uploadAvatar(user: User): IO[Avatar]

  def uploadExternalCfpLogo(cfp: ExternalCfp, logo: Logo): IO[Logo]

  def uploadGroupLogo(group: Group, logo: Logo): IO[Logo]

  def uploadGroupBanner(group: Group, banner: Banner): IO[Banner]

  def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo]
}
