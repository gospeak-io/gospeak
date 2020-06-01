package gospeak.web.services

import java.time.Instant

import cats.effect.{IO, Timer}
import cron4s.CronExpr
import eu.timepit.fs2cron.awakeEveryCron
import fs2.Stream
import gospeak.core.domain.utils.{AdminCtx, Constants}
import gospeak.core.services.storage.AdminVideoRepo
import gospeak.core.services.twitter.TwitterSrv
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.TimeUtils
import gospeak.web.services.SchedulerSrv.{Conf, Exec, Scheduler}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class SchedulerSrv(videoRepo: AdminVideoRepo,
                   twitterSrv: Option[TwitterSrv])(implicit ec: ExecutionContext) {
  implicit private val timer: Timer[IO] = IO.timer(ec)
  private val schedulers = mutable.ListBuffer[Scheduler]()
  private val execs: mutable.ListBuffer[Exec] = mutable.ListBuffer[Exec]()

  def getSchedulers: List[Scheduler] = schedulers.toList

  def getExecs: List[Exec] = execs.toList

  def init(conf: Conf): Unit = {
    schedule("tweet random video", conf.tweetRandomVideo, tweetRandomVideo())
  }

  def exec(name: String)(implicit ctx: AdminCtx): IO[Option[Exec]] =
    schedulers.find(_.name == name).map(exec(_, s"manual (${ctx.user.name.value})")).sequence

  private def tweetRandomVideo(): IO[(String, Option[String])] = for {
    video <- videoRepo.findRandom()
    tweet <- (for {
      srv <- twitterSrv.toRight("Twitter service not available")
      v <- video.toRight("No video available")
    } yield srv.tweet(s"#OneDayOneTalk [${v.lang}] ${v.title} on ${v.channel.name} in ${v.publishedAt.getYear(Constants.defaultZoneId)} ${v.url.value}")).sequence
  } yield (tweet.map(t => s"Tweet sent: ${t.text}").getOrElse("Tweet not sent"), tweet.swap.toOption)

  // TODO be able to stop/start a scheduler
  private def schedule(name: String, cron: CronExpr, task: IO[(String, Option[String])]): Unit = {
    schedulers.find(_.name == name).map(_ => ()).getOrElse {
      val scheduler = Scheduler(name, cron, Some(Instant.now()), task)
      schedulers += scheduler
      val stream = awakeEveryCron[IO](cron).flatMap { _ => Stream.eval(exec(scheduler, "auto")) }
      stream.compile.drain.unsafeRunAsyncAndForget
    }
  }

  private def exec(s: Scheduler, source: String): IO[Exec] = IO(Instant.now()).flatMap { start =>
    s.task.map {
      case (res, None) => Exec(s.name, source, start, Instant.now(), res, None)
      case (res, Some(err)) => Exec(s.name, source, start, Instant.now(), res, Some(err))
    }.recover {
      case NonFatal(e) => Exec(s.name, source, start, Instant.now(), s"Finished with ${e.getClass.getSimpleName}", Some(e.getMessage))
    }.map { e =>
      execs += e
      e
    }
  }
}

object SchedulerSrv {

  final case class Conf(tweetRandomVideo: CronExpr)

  final case class Scheduler(name: String,
                             schedule: CronExpr,
                             started: Option[Instant],
                             private[SchedulerSrv] val task: IO[(String, Option[String])])

  final case class Exec(name: String,
                        source: String,
                        started: Instant,
                        finished: Instant,
                        result: String,
                        error: Option[String]) {
    def duration: FiniteDuration = TimeUtils.toFiniteDuration(started, finished)
  }

}
