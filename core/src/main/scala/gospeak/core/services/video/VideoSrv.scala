package gospeak.core.services.video

import cats.effect.IO
import gospeak.core.domain.Video
import gospeak.libs.scala.domain.Url

trait VideoSrv {
  def getChannelId(url: Url.Videos.Channel): IO[String]

  def listVideos(url: Url.Videos): IO[List[Video.Data]]
}
