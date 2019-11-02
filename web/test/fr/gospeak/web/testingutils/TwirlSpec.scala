package fr.gospeak.web.testingutils

import com.mohiva.play.silhouette.api.actions.{SecuredRequest, UserAwareRequest}
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.domain.Breadcrumb
import org.scalatest.{FunSpec, Matchers}
import play.api.i18n.Messages
import play.api.mvc.AnyContent

trait TwirlSpec extends FunSpec with Matchers {
  protected implicit val userAwareReq: UserAwareRequest[CookieEnv, AnyContent] = Values.userAwareReq
  protected implicit val securedReq: SecuredRequest[CookieEnv, AnyContent] = Values.securedReq
  protected implicit val messages: Messages = Values.messages
  protected val b: Breadcrumb = Values.b
}
