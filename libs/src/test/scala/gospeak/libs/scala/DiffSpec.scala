package gospeak.libs.scala

import gospeak.libs.testingutils.BaseSpec

class DiffSpec extends BaseSpec {
  describe("Diff") {
    it("should diff simple lists") {
      Diff.from(List(1, 2, 3), List(2, 4, 6)) shouldBe Diff(List(1, 3), List(2 -> 2), List(4, 6))
    }
  }
}
