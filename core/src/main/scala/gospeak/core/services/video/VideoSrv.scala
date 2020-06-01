package gospeak.core.services.video

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.libs.scala.domain.Url

trait VideoSrv {
  val youtube: Boolean
  val vimeo: Boolean
  val infoq: Boolean

  def getChannelId(url: Url.Videos.Channel): IO[Url.Videos.Channel.Id]

  def listVideos(url: Url.Videos): IO[List[Video.Data]]
}
