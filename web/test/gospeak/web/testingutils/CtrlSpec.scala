package gospeak.web.testingutils

import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import com.mohiva.play.silhouette.api.Silhouette
import gospeak.infra.services.email.InMemoryEmailSrv
import gospeak.infra.services.storage.sql.GsRepoSql
import gospeak.web.AppConf
import gospeak.web.auth.domain.CookieEnv
import gospeak.web.auth.services.AuthSrv
import play.api.mvc._

trait CtrlSpec extends BaseSpec {
  // play
  protected val cc: ControllerComponents = Values.cc

  // silhouette
  protected val silhouette: Silhouette[CookieEnv] = Values.silhouette
  protected val unsecuredReq: RequestHeader = Values.unsecuredReqHeader
  protected val securedReq: RequestHeader = Values.securedReqHeader
  protected implicit val mat: Materializer = NoMaterializer

  // app
  protected val conf: AppConf = Values.conf
  protected val db: GsRepoSql = Values.db
  protected val emailSrv: InMemoryEmailSrv = Values.emailSrv
  protected val authSrv: AuthSrv = Values.authSrv
}
