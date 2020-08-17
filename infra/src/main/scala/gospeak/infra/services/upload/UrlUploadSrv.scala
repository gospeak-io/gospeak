package gospeak.infra.services.upload

import cats.effect.IO
import gospeak.core.domain.{ExternalEvent, Group, Partner, User}
import gospeak.core.services.cloudinary.UploadSrv
import gospeak.libs.scala.domain.{Avatar, Banner, CustomException, Logo}

class UrlUploadSrv extends UploadSrv {
  private val err = "Cloudinary service not implemented"

  override def signRequest(params: Map[String, String]): Either[String, String] = Left(err)

  override def uploadAvatar(user: User): IO[Avatar] = IO.raiseError(CustomException(err))

  override def uploadGroupLogo(group: Group, logo: Logo): IO[Logo] = IO.raiseError(CustomException(err))

  override def uploadGroupBanner(group: Group, banner: Banner): IO[Banner] = IO.raiseError(CustomException(err))

  override def uploadPartnerLogo(group: Group, partner: Partner): IO[Logo] = IO.raiseError(CustomException(err))

  override def uploadExternalEventLogo(event: ExternalEvent, logo: Logo): IO[Logo] = IO.raiseError(CustomException(err))
}
