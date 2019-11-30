package fr.gospeak.web.testingutils

import akka.stream.Materializer
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.infra.services.InMemoryEmailSrv
import fr.gospeak.infra.services.storage.sql.GospeakDbSql
import fr.gospeak.web.AppConf
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.auth.services.AuthSrv
import play.api.mvc._
import play.api.test.NoMaterializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

trait CtrlSpec extends AnyFunSpec with Matchers {
  // play
  protected val cc: ControllerComponents = Values.cc

  // silhouette
  protected val silhouette: Silhouette[CookieEnv] = Values.silhouette
  protected val unsecuredReq: RequestHeader = Values.unsecuredReqHeader
  protected val securedReq: RequestHeader = Values.securedReqHeader
  protected implicit val mat: Materializer = NoMaterializer

  // app
  protected val conf: AppConf = Values.conf
  protected val db: GospeakDbSql = Values.db
  protected val emailSrv: InMemoryEmailSrv = Values.emailSrv
  protected val authSrv: AuthSrv = Values.authSrv

  protected val timeShape: TimeShape = Values.timeShape
}
