package gospeak.libs.youtube.domain

final case class YoutubePage[T](items: List[T],
                                nextPageToken: Option[String])
