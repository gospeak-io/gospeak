package gospeak.libs.youtube.utils

import java.time.Instant

import com.google.api.client.util.DateTime

object YoutubeParser {
  def toInstant(d: DateTime): Instant = Instant.parse(d.toStringRfc3339)
}
