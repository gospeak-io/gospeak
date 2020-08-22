package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.implicits._
import gospeak.core.domain._
import gospeak.core.domain.utils.{AdminCtx, BasicCtx, UserAwareCtx}
import gospeak.core.services.storage.VideoRepo
import gospeak.infra.services.storage.sql.VideoRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Done, Page, Url}
import gospeak.libs.sql.doobie.{DbCtx, Field, Query, Table}

import scala.util.control.NonFatal

class VideoRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with VideoRepo {
  override def create(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Done] = for {
    exists <- selectOne(video.id).runOption(xa)
    _ <- exists.map(v => update(video, ctx.now).run(xa)).getOrElse(insert(Video(video, ctx.now)).run(xa))
    _ <- insert(video.id, event).run(xa)
  } yield Done

  override def edit(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Done] =
    for {
      _ <- update(video, ctx.now).run(xa)
      exists <- selectOne(video.id, event).runExists(xa)
      _ <- if (exists) IO.pure((video.id, event)) else insert(video.id, event).run(xa)
    } yield Done

  // true if deleted, false otherwise (other links referencing the video)
  override def remove(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Boolean] = for {
    _ <- delete(video.id, event).run(xa).recover { case NonFatal(_) => Done }
    c <- sum(video.id).runOption(xa).map(_.getOrElse(0))
    r <- if (c == 0) delete(video.url).run(xa).map(_ => true) else IO.pure(false)
  } yield r

  override def find(video: Url.Video.Id): IO[Option[Video]] = selectOne(video).runOption(xa)

  def findRandom(): IO[Option[Video]] = selectOneRandom().runOption(xa)

  override def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]] = selectPage(params).run(xa)

  override def listAll(event: ExternalEvent.Id): IO[List[Video]] = selectAll(event).runList(xa)

  override def listAllForChannel(channelId: Url.Videos.Channel.Id): IO[List[Video]] = selectAllForChannel(channelId).runList(xa)

  override def listAllForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[List[Video]] = selectAllForPlaylist(playlistId).runList(xa)

  override def count(event: ExternalEvent.Id): IO[Long] = sum(event).runOption(xa).map(_.getOrElse(0))

  override def countForChannel(channelId: Url.Videos.Channel.Id): IO[Long] = countChannelId(channelId).runOption(xa).map(_.getOrElse(0))

  override def countForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[Long] = countPlaylistId(playlistId).runOption(xa).map(_.getOrElse(0))
}

object VideoRepoSql {
  private val _ = urlMeta // for intellij not remove DoobieUtils.Mappings import
  private val tableSources = Tables.videoSources
  private val table = Tables.videos
  private val tableSelect = table.dropField(_.platform).get.dropField(_.id).get
  private val tableWithSources = table
    .join(tableSources, _.id -> _.video_id).get
    .dropFields(_.prefix != table.prefix)
    .dropField(_.platform).get
    .dropField(_.id).get

  private[sql] def insert(v: Url.Video.Id, e: ExternalEvent.Id): Query.Insert[(Url.Video.Id, ExternalEvent.Id)] =
    tableSources.insert[(Url.Video.Id, ExternalEvent.Id)](v -> e, _ => fr0"$v, NULL, NULL, NULL, $e")

  private[sql] def delete(v: Url.Video.Id, e: ExternalEvent.Id): Query.Delete =
    tableSources.delete.where(fr0"video_id=$v AND external_event_id=$e")

  private[sql] def selectOne(v: Url.Video.Id, e: ExternalEvent.Id): Query.Select[Video.Sources] =
    tableSources.select[Video.Sources].where(fr0"video_id=$v AND external_event_id=$e")

  private[sql] def sum(v: Url.Video.Id): Query.Select[Long] =
    tableSources.select[Long].fields(Field("COUNT(*)", "")).where(fr0"video_id=$v GROUP BY vis.video_id").one

  private[sql] def sum(event: ExternalEvent.Id): Query.Select[Long] =
    tableSources.select[Long].fields(Field("COUNT(*)", "")).where(fr0"external_event_id=$event GROUP BY vis.external_event_id").sort(Table.Sort("external_event_id", "vis")).one

  private[sql] def insert(e: Video): Query.Insert[Video] = {
    val values = fr0"${e.url.platform}, ${e.url}, ${e.id}, ${e.channel.id}, ${e.channel.name}, ${e.playlist.map(_.id)}, ${e.playlist.map(_.name)}, ${e.title}, ${e.description}, ${e.tags}, ${e.publishedAt}, ${e.duration}, ${e.lang}, ${e.views}, ${e.likes}, ${e.dislikes}, ${e.comments}, ${e.updatedAt}"
    table.insert[Video](e, _ => values)
  }

  private[sql] def update(data: Video.Data, now: Instant): Query.Update = {
    val fields = fr0"title=${data.title}, description=${data.description}, tags=${data.tags}, duration=${data.duration}, lang=${data.lang}, views=${data.views}, likes=${data.likes}, dislikes=${data.dislikes}, comments=${data.comments}, updated_at=$now"
    table.update(fields).where(fr0"id=${data.id}")
  }

  private[sql] def delete(url: Url.Video): Query.Delete =
    table.delete.where(fr0"id=${url.videoId}")

  private[sql] def selectOne(video: Url.Video.Id): Query.Select[Video] =
    tableSelect.select[Video].where(fr0"vi.id=$video").one

  private[sql] def selectOneRandom(): Query.Select[Video] =
    tableSelect.select[Video].offset(fr0"FLOOR(RANDOM() * (SELECT COUNT(*) FROM videos))").one

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserAwareCtx): Query.SelectPage[Video] =
    tableSelect.selectPage[Video](params, adapt(ctx))

  private[sql] def selectAll(event: ExternalEvent.Id): Query.Select[Video] =
    tableWithSources.select[Video].where(fr0"vis.external_event_id=$event")

  private[sql] def selectAllForChannel(channelId: Url.Videos.Channel.Id): Query.Select[Video] =
    tableSelect.select[Video].where(fr0"vi.channel_id=$channelId")

  private[sql] def selectAllForPlaylist(playlistId: Url.Videos.Playlist.Id): Query.Select[Video] =
    tableSelect.select[Video].where(fr0"vi.playlist_id=$playlistId")

  private[sql] def countChannelId(channelId: Url.Videos.Channel.Id): Query.Select[Long] =
    tableSelect.select[Long].fields(Field("COUNT(*)", "")).where(fr0"vi.channel_id=$channelId GROUP BY vi.channel_id").sort(Table.Sort("channel_id", "vi"))

  private[sql] def countPlaylistId(playlistId: Url.Videos.Playlist.Id): Query.Select[Long] =
    tableSelect.select[Long].fields(Field("COUNT(*)", "")).where(fr0"vi.playlist_id=$playlistId GROUP BY vi.playlist_id").sort(Table.Sort("playlist_id", "vi"))

  private def adapt(ctx: BasicCtx): DbCtx = DbCtx(ctx.now)
}
