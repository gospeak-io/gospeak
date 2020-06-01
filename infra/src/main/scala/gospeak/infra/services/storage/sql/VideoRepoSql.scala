package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain._
import gospeak.core.domain.utils.{AdminCtx, UserAwareCtx}
import gospeak.core.services.storage.VideoRepo
import gospeak.infra.services.storage.sql.VideoRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Delete, Field, Insert, Select, SelectPage, Sort, Update}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, Page, Url}

import scala.util.control.NonFatal

class VideoRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with VideoRepo {
  override def create(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Video] = for {
    v <- insert(Video(video, ctx.now)).run(xa)
    _ <- insert(v.url.videoId, event).run(xa)
  } yield v

  override def edit(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Done] =
    for {
      _ <- update(video, ctx.now).run(xa)
      exists <- selectOne(video.url.videoId, event).runExists(xa)
      _ <- if (exists) IO.pure((video.url.videoId, event)) else insert(video.url.videoId, event).run(xa)
    } yield Done

  // true if deleted, false otherwise (other links referencing the video)
  override def remove(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Boolean] = for {
    _ <- delete(video.url.videoId, event).run(xa).recover { case NonFatal(_) => Done }
    c <- count(video.url.videoId).runOption(xa).map(_.getOrElse(0))
    r <- if (c == 0) delete(video.url).run(xa).map(_ => true) else IO.pure(false)
  } yield r

  override def find(video: Url.Video.Id): IO[Option[Video]] = selectOne(video).runOption(xa)

  def findRandom(): IO[Option[Video]] = selectOneRandom().runOption(xa)

  override def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]] = selectPage(params).run(xa)

  override def listAllForChannel(channelId: Url.Videos.Channel.Id): IO[List[Video]] = selectAllForChannel(channelId).runList(xa)

  override def listAllForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[List[Video]] = selectAllForPlaylist(playlistId).runList(xa)

  override def countForChannel(channelId: Url.Videos.Channel.Id): IO[Long] = countChannelId(channelId).runOption(xa).map(_.getOrElse(0))

  override def countForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[Long] = countPlaylistId(playlistId).runOption(xa).map(_.getOrElse(0))
}

object VideoRepoSql {
  private val _ = urlMeta // for intellij not remove DoobieUtils.Mappings import
  private val tableSources = Tables.videoSources
  private val table = Tables.videos
  private val tableSelect = table.dropField(_.platform).get.dropField(_.id).get

  private[sql] def insert(v: Url.Video.Id, e: ExternalEvent.Id): Insert[(Url.Video.Id, ExternalEvent.Id)] =
    tableSources.insert[(Url.Video.Id, ExternalEvent.Id)](v -> e, _ => fr0"$v, NULL, NULL, NULL, $e")

  private[sql] def delete(v: Url.Video.Id, e: ExternalEvent.Id): Delete =
    tableSources.delete(fr0"WHERE video_id=$v AND external_event_id=$e")

  private[sql] def selectOne(v: Url.Video.Id, e: ExternalEvent.Id): Select[Video.Sources] =
    tableSources.select[Video.Sources](fr0"WHERE video_id=$v AND external_event_id=$e")

  private[sql] def count(v: Url.Video.Id): Select[Long] =
    tableSources.selectOne[Long](Seq(Field("COUNT(*)", "")), fr0"WHERE video_id=$v GROUP BY vis.video_id")

  private[sql] def insert(e: Video): Insert[Video] = {
    val values = fr0"${e.url.platform}, ${e.url}, ${e.url.videoId}, ${e.channel.id}, ${e.channel.name}, ${e.playlist.map(_.id)}, ${e.playlist.map(_.name)}, ${e.title}, ${e.description}, ${e.tags}, ${e.publishedAt}, ${e.duration}, ${e.lang}, ${e.views}, ${e.likes}, ${e.dislikes}, ${e.comments}, ${e.updatedAt}"
    table.insert[Video](e, _ => values)
  }

  private[sql] def update(data: Video.Data, now: Instant): Update = {
    val fields = fr0"title=${data.title}, description=${data.description}, tags=${data.tags}, duration=${data.duration}, lang=${data.lang}, views=${data.views}, likes=${data.likes}, dislikes=${data.dislikes}, comments=${data.comments}, updated_at=$now"
    table.update(fields, fr0"WHERE id=${data.url.videoId}")
  }

  private[sql] def delete(url: Url.Video): Delete =
    table.delete(fr0"WHERE id=${url.videoId}")

  private[sql] def selectOne(video: Url.Video.Id): Select[Video] =
    tableSelect.selectOne[Video](fr0"WHERE vi.id=$video")

  private[sql] def selectOneRandom(): Select[Video] =
    tableSelect.selectOne[Video](fr0"", offset = fr0"FLOOR(RANDOM() * (SELECT COUNT(*) FROM videos))")

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserAwareCtx): SelectPage[Video, UserAwareCtx] =
    tableSelect.selectPage[Video, UserAwareCtx](params)

  private[sql] def selectAllForChannel(channelId: Url.Videos.Channel.Id): Select[Video] =
    tableSelect.select[Video](fr0"WHERE vi.channel_id=$channelId")

  private[sql] def selectAllForPlaylist(playlistId: Url.Videos.Playlist.Id): Select[Video] =
    tableSelect.select[Video](fr0"WHERE vi.playlist_id=$playlistId")

  private[sql] def countChannelId(channelId: Url.Videos.Channel.Id): Select[Long] =
    tableSelect.select[Long](Seq(Field("COUNT(*)", "")), fr0"WHERE vi.channel_id=$channelId GROUP BY vi.channel_id", Sort("channel_id", "vi"))

  private[sql] def countPlaylistId(playlistId: Url.Videos.Playlist.Id): Select[Long] =
    tableSelect.select[Long](Seq(Field("COUNT(*)", "")), fr0"WHERE vi.playlist_id=$playlistId GROUP BY vi.playlist_id", Sort("playlist_id", "vi"))
}
