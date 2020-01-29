package gospeak.web.testingutils

import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.infra.services.email.InMemoryEmailSrv
import gospeak.infra.services.storage.sql.GospeakDbSql
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.auth.services.AuthSrv
import org.scalatest.{FunSpec, Matchers}
import play.api.mvc._

trait CtrlSpec extends FunSpec with Matchers {
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
}
