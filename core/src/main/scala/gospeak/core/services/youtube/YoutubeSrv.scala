package gospeak.core.services.youtube

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.libs.scala.domain.CustomException

trait YoutubeSrv {
  def videos(channelId: String)(now : Instant): IO[Either[CustomException, Seq[Video]]]
}
