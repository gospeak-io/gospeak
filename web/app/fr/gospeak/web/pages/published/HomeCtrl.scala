package fr.gospeak.web.pages.published

import cats.effect.IO
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.pages.published.HomeCtrl._
import fr.gospeak.web.utils.UICtrl
import org.slf4j.LoggerFactory
import play.api.mvc._

class HomeCtrl(cc: ControllerComponents,
               silhouette: Silhouette[CookieEnv],
               conf: AppConf) extends UICtrl(cc, silhouette, conf) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def index(): Action[AnyContent] = UserAwareAction { implicit req =>
    logger.info(s"version: ${req.version}")
    logger.info("Session:")
    req.session.data.foreach { case (key, value) => logger.info(s"  - $key: $value") }
    logger.info("Cookies:")
    req.cookies.toList.foreach(c => logger.info(s"  - ${c.name}: ${c.value}"))
    logger.info("Headers:")
    req.headers.toMap.foreach { case (key, value) => logger.info(s"  - $key: ${value.mkString(", ")}") }

    IO.pure(Ok(html.index()(breadcrumb())))
  }

  def why(): Action[AnyContent] = UserAwareAction { implicit req =>
    IO.pure(Ok(html.why()(breadcrumb().add("Why use Gospeak" -> routes.HomeCtrl.why()))))
  }
}

object HomeCtrl {
  def breadcrumb(): Breadcrumb =
    Breadcrumb("Home" -> routes.HomeCtrl.index())
}
