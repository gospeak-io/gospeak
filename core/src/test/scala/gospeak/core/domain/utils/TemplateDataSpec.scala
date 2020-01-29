package gospeak.core.domain.utils

import gospeak.core.domain.Group
import org.scalatest.{FunSpec, Matchers}

class TemplateDataSpec extends FunSpec with Matchers {
  describe("TemplateData") {
    describe("Ref") {
      describe("from") {
        it("should build Ref from TemplateData classes") {
          TemplateData.Sample.all.map { s =>
            TemplateData.Ref.from(s.getClass.getSimpleName).right.get.value shouldBe s.getClass.getSimpleName
          }
        }
        it("should build Ref from Group.Settings.Action.Trigger objects") {
          Group.Settings.Action.Trigger.all.map { t =>
            TemplateData.Ref.from(t.getClassName) shouldBe a[Right[_, _]]
          }
        }
        it("should fail on unknown values") {
          Seq("", "aaa").map { v =>
            TemplateData.Ref.from(v) shouldBe a[Left[_, _]]
          }
        }
      }
    }
  }
}
