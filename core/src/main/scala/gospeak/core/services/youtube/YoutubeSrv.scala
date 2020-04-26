package gospeak.core.services.youtube

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.libs.scala.domain.CustomException

trait YoutubeSrv {
  def videos(channel: ChannelRef)(now: Instant): IO[Either[CustomException, Seq[Video]]]

  def videos(channel: PlaylistRef)(now: Instant): IO[Either[CustomException, Seq[Video]]]
}
