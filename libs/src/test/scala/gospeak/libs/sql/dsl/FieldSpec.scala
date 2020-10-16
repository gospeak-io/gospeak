package gospeak.libs.sql.dsl

import gospeak.libs.testingutils.BaseSpec

class FieldSpec extends BaseSpec {
  describe("Field") {
    describe("Order") {
      it("should build order with nulls last") {
        Field.Order(TableField("name"), asc = true, None).fr(nullsFirst = false).query.sql shouldBe "name IS NULL, name"
        // Field.Order(TableField("null").as("name"), asc = true, None).fr(nullsFirst = false).query.sql shouldBe "name IS NULL, name"
        Field.Order(TableField("name"), asc = false, None).fr(nullsFirst = false).query.sql shouldBe "name IS NULL, name DESC"
      }
      it("should build order with nulls first") {
        Field.Order(TableField("name"), asc = true, None).fr(nullsFirst = true).query.sql shouldBe "name IS NOT NULL, name"
      }
    }
  }
}
