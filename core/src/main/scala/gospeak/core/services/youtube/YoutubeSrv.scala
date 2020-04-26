package gospeak.core.services.youtube

import java.time.Instant

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.core.domain.Video.{ChannelRef, PlaylistRef}
import gospeak.libs.scala.domain.CustomException

trait YoutubeSrv {
  def channelVideos(channel: ChannelRef)(now: Instant): IO[Either[CustomException, Seq[Video]]]

  def playlistVideos(channel: PlaylistRef)(now: Instant): IO[Either[CustomException, Seq[Video]]]
}
