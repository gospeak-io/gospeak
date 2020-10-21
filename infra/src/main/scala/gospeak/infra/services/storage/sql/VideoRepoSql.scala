package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.effect.IO
import doobie.syntax.string._
import gospeak.core.domain._
import gospeak.core.domain.utils.{AdminCtx, UserAwareCtx}
import gospeak.core.services.storage.VideoRepo
import gospeak.infra.services.storage.sql.VideoRepoSql._
import gospeak.infra.services.storage.sql.database.Tables.{VIDEOS, VIDEO_SOURCES}
import gospeak.infra.services.storage.sql.database.tables.{VIDEOS, VIDEO_SOURCES}
import gospeak.infra.services.storage.sql.utils.DoobieMappings._
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain.{Page, Url}
import gospeak.libs.sql.dsl.Expr.{Floor, Random, SubQuery}
import gospeak.libs.sql.dsl.{AggField, Query}

import scala.util.control.NonFatal

class VideoRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with VideoRepo {
  override def create(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Video] = for {
    exists <- selectOne(video.id).run(xa)
    v = Video(video, ctx.now)
    _ <- exists.map(_ => update(video, ctx.now).run(xa).map(_ => v)).getOrElse(insert(v).run(xa))
    _ <- insert(video.id, event).run(xa)
  } yield v

  override def edit(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Unit] =
    for {
      _ <- update(video, ctx.now).run(xa)
      exists <- selectOne(video.id, event).run(xa)
      _ <- if (exists) IO.pure((video.id, event)) else insert(video.id, event).run(xa)
    } yield ()

  // true if deleted, false otherwise (other links referencing the video)
  override def remove(video: Video.Data, event: ExternalEvent.Id)(implicit ctx: AdminCtx): IO[Boolean] = for {
    _ <- delete(video.id, event).run(xa).recover { case NonFatal(_) => () }
    c <- sum(video.id).run(xa).map(_.getOrElse(0))
    r <- if (c == 0) delete(video.url).run(xa).map(_ => true) else IO.pure(false)
  } yield r

  override def find(video: Url.Video.Id): IO[Option[Video]] = selectOne(video).run(xa)

  def findRandom(): IO[Option[Video]] = selectOneRandom().run(xa)

  override def list(params: Page.Params)(implicit ctx: UserAwareCtx): IO[Page[Video]] = selectPage(params).run(xa)

  override def listAll(event: ExternalEvent.Id): IO[List[Video]] = selectAll(event).run(xa)

  override def listAllForChannel(channelId: Url.Videos.Channel.Id): IO[List[Video]] = selectAllForChannel(channelId).run(xa)

  override def listAllForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[List[Video]] = selectAllForPlaylist(playlistId).run(xa)

  override def count(event: ExternalEvent.Id): IO[Long] = sum(event).run(xa).map(_.getOrElse(0))

  override def countForChannel(channelId: Url.Videos.Channel.Id): IO[Long] = countChannelId(channelId).run(xa).map(_.getOrElse(0))

  override def countForPlaylist(playlistId: Url.Videos.Playlist.Id): IO[Long] = countPlaylistId(playlistId).run(xa).map(_.getOrElse(0))
}

object VideoRepoSql {
  private val VIDEOS_SELECT = VIDEOS.dropFields(VIDEOS.PLATFORM, VIDEOS.ID)
  private val VIDEOS_WITH_SOURCES = VIDEOS.join(VIDEO_SOURCES).on(_.ID is _.VIDEO_ID).fields(VIDEOS_SELECT.getFields)

  private[sql] def insert(v: Url.Video.Id, e: ExternalEvent.Id): Query.Insert[VIDEO_SOURCES] =
  // VIDEO_SOURCES.insert.values(v, Option.empty[Talk.Id], Option.empty[Proposal.Id], Option.empty[ExternalProposal.Id], e)
    VIDEO_SOURCES.insert.values(fr0"$v, ${Option.empty[Talk.Id]}, ${Option.empty[Proposal.Id]}, ${Option.empty[ExternalProposal.Id]}, $e")

  private[sql] def delete(v: Url.Video.Id, e: ExternalEvent.Id): Query.Delete[VIDEO_SOURCES] =
    VIDEO_SOURCES.delete.where(vis => vis.VIDEO_ID.is(v) and vis.EXTERNAL_EVENT_ID.is(e))

  private[sql] def selectOne(v: Url.Video.Id, e: ExternalEvent.Id): Query.Select.Exists[Video.Sources] =
    VIDEO_SOURCES.select.where(vis => vis.VIDEO_ID.is(v) and vis.EXTERNAL_EVENT_ID.is(e)).exists[Video.Sources]

  private[sql] def sum(v: Url.Video.Id): Query.Select.Optional[Long] =
    VIDEO_SOURCES.select.fields(AggField("COUNT(*)")).where(_.VIDEO_ID is v).groupBy(VIDEO_SOURCES.VIDEO_ID).option[Long](limit = true)

  private[sql] def sum(event: ExternalEvent.Id): Query.Select.Optional[Long] =
    VIDEO_SOURCES.select.fields(AggField("COUNT(*)")).where(_.EXTERNAL_EVENT_ID is event).groupBy(VIDEO_SOURCES.EXTERNAL_EVENT_ID).orderBy(VIDEO_SOURCES.EXTERNAL_EVENT_ID.asc).option[Long](limit = true)

  private[sql] def insert(e: Video): Query.Insert[VIDEOS] =
  // VIDEOS.insert.values(e.url.platform, e.url, e.id, e.channel.id, e.channel.name, e.playlist.map(_.id), e.playlist.map(_.name), e.title, e.description, e.tags, e.publishedAt, e.duration, e.lang, e.views, e.likes, e.dislikes, e.comments, e.updatedAt)
    VIDEOS.insert.values(fr0"${e.url.platform}, ${e.url}, ${e.id}, ${e.channel.id}, ${e.channel.name}, ${e.playlist.map(_.id)}, ${e.playlist.map(_.name)}, ${e.title}, ${e.description}, ${e.tags}, ${e.publishedAt}, ${e.duration}, ${e.lang}, ${e.views}, ${e.likes}, ${e.dislikes}, ${e.comments}, ${e.updatedAt}")

  private[sql] def update(data: Video.Data, now: Instant): Query.Update[VIDEOS] =
    VIDEOS.update.set(_.TITLE, data.title).set(_.DESCRIPTION, data.description).set(_.TAGS, data.tags).set(_.DURATION, data.duration).set(_.LANG, data.lang).set(_.VIEWS, data.views).set(_.LIKES, data.likes).set(_.DISLIKES, data.dislikes).set(_.COMMENTS, data.comments).set(_.UPDATED_AT, now).where(_.ID is data.id)

  private[sql] def delete(url: Url.Video): Query.Delete[VIDEOS] =
    VIDEOS.delete.where(_.ID is url.videoId)

  private[sql] def selectOne(video: Url.Video.Id): Query.Select.Optional[Video] =
    VIDEOS_SELECT.select.where(VIDEOS.ID is video).option[Video](limit = true)

  private[sql] def selectOneRandom(): Query.Select.Optional[Video] =
    VIDEOS_SELECT.select.offset(Floor(Random() * SubQuery(VIDEOS.select.fields(AggField("COUNT(*)")).orderBy().one[Long]))).option[Video](limit = true)

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserAwareCtx): Query.Select.Paginated[Video] =
    VIDEOS_SELECT.select.page[Video](params, ctx.toDb)

  private[sql] def selectAll(event: ExternalEvent.Id): Query.Select.All[Video] =
    VIDEOS_WITH_SOURCES.select.where(VIDEO_SOURCES.EXTERNAL_EVENT_ID is event).all[Video]

  private[sql] def selectAllForChannel(channelId: Url.Videos.Channel.Id): Query.Select.All[Video] =
    VIDEOS_SELECT.select.where(VIDEOS.CHANNEL_ID is channelId).all[Video]

  private[sql] def selectAllForPlaylist(playlistId: Url.Videos.Playlist.Id): Query.Select.All[Video] =
    VIDEOS_SELECT.select.where(VIDEOS.PLAYLIST_ID is playlistId).all[Video]

  private[sql] def countChannelId(channelId: Url.Videos.Channel.Id): Query.Select.Optional[Long] =
    VIDEOS.select.fields(AggField("COUNT(*)")).where(_.CHANNEL_ID is channelId).groupBy(VIDEOS.CHANNEL_ID).orderBy(VIDEOS.CHANNEL_ID.asc).option[Long]

  private[sql] def countPlaylistId(playlistId: Url.Videos.Playlist.Id): Query.Select.Optional[Long] =
    VIDEOS.select.fields(AggField("COUNT(*)")).where(_.PLAYLIST_ID is playlistId).groupBy(VIDEOS.PLAYLIST_ID).orderBy(VIDEOS.PLAYLIST_ID.asc).option[Long]
}
