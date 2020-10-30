package gospeak.infra.testingutils

import org.scalamock.scalatest.MockFactory
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

abstract class BaseSpec extends AnyFunSpec with Matchers with MockFactory
