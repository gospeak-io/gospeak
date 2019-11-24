package fr.gospeak.web.testingutils

import fr.gospeak.web.domain.Breadcrumb
import fr.gospeak.web.utils.{UserAwareReq, UserReq}
import org.scalatest.{FunSpec, Matchers}
import play.api.mvc.AnyContent

trait TwirlSpec extends FunSpec with Matchers {
  protected implicit val userReq: UserReq[AnyContent] = Values.userReq
  protected implicit val userAwareReq: UserAwareReq[AnyContent] = Values.userAwareReq
  protected val b: Breadcrumb = Values.b
}
