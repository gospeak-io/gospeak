package gospeak.core.services.youtube

import cats.effect.IO
import gospeak.core.domain.Video

trait YoutubeSrv {
  def search(channelId: String, itemType: String): IO[Seq[Video]]
}
