package gospeak.libs.sql.dsl

import gospeak.libs.scala.domain.Page
import gospeak.libs.sql.testingutils.Entities.{Category, User}
import gospeak.libs.sql.testingutils.database.Tables.{CATEGORIES, USERS}
import gospeak.libs.testingutils.BaseSpec

class QuerySpec extends BaseSpec {
  private val p = Page.Params.defaults

  describe("Query") {
    describe("Insert") {

    }
    describe("Update") {

    }
    describe("Delete") {

    }
    describe("Select") {
      describe("page") {
        it("should build a paginated query") {
          USERS.select.page[User](p).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 20 OFFSET 0"
        }
        it("should change pagination based on page no and size") {
          USERS.select.page[User](p.copy(page = Page.No(3))).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 20 OFFSET 40"
          USERS.select.page[User](p.copy(pageSize = Page.Size(5))).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 5 OFFSET 0"
          USERS.select.page[User](p.copy(page = Page.No(2), pageSize = Page.Size(10))).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 10 OFFSET 10"
        }
        describe("search") {
          it("should search on all fields when not specified") {
            USERS.select.page[User](p.copy(search = Some(Page.Search("q")))).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.id ILIKE ? OR u.name ILIKE ? OR u.email ILIKE ? LIMIT 20 OFFSET 0"
            USERS.select.where(_.ID lt User.Id(10)).page[User](p).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.id < ? LIMIT 20 OFFSET 0"
            USERS.select.where(_.ID lt User.Id(10)).page[User](p.copy(search = Some(Page.Search("q")))).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.id < ? AND (u.id ILIKE ? OR u.name ILIKE ? OR u.email ILIKE ?) LIMIT 20 OFFSET 0"
          }
          it("should search on fields specified on table") {
            CATEGORIES.select.orderBy().page[Category](p.copy(search = Some(Page.Search("q")))).sql shouldBe
              "SELECT c.id, c.name FROM categories c WHERE c.name ILIKE ? LIMIT 20 OFFSET 0"
          }
        }
        describe("order") {
          it("should handle custom order") {
            USERS.select.page[User](p).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u LIMIT 20 OFFSET 0"
            USERS.select.page[User](p.copy(orderBy = Some(Page.OrderBy("name", "-id")))).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name, u.id IS NULL, u.id DESC LIMIT 20 OFFSET 0"
          }
          it("should use table orders when exists") {
            val users = USERS.addSort(Table.Sort("test", USERS.NAME.asc, USERS.ID.desc))
            users.select.page[User](p.copy(orderBy = Some(Page.OrderBy("test")))).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name, u.id IS NULL, u.id DESC LIMIT 20 OFFSET 0"
            users.select.page[User](p.copy(orderBy = Some(Page.OrderBy("-test")))).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name DESC, u.id IS NULL, u.id LIMIT 20 OFFSET 0"
          }
          it("should ignore invalid orders") {
            USERS.select.page[User](p.copy(orderBy = Some(Page.OrderBy("name", "toto")))).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name LIMIT 20 OFFSET 0"
          }
        }
        it("should handle custom filters") {
          USERS.select.page[User](p.copy(filters = Map())).sql shouldBe ""
        }
      }
    }
  }
}
