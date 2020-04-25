package gospeak.core.services.youtube

import cats.effect.IO
import gospeak.core.domain.YoutubeVideo

trait YoutubeSrv {
  def videos(channelId: String): IO[Seq[YoutubeVideo]]
}
