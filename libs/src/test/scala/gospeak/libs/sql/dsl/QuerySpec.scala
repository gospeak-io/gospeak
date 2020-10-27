package gospeak.libs.sql.dsl

import java.time.Instant

import doobie.util.Put
import doobie.util.meta.Meta
import gospeak.libs.scala.domain.Page
import gospeak.libs.sql.dsl.Query.Inner._
import gospeak.libs.sql.testingutils.Entities.{Category, User}
import gospeak.libs.sql.testingutils.database.Tables.{CATEGORIES, POSTS, USERS}
import gospeak.libs.testingutils.BaseSpec

class QuerySpec extends BaseSpec {
  private implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  private val ctx: Query.Ctx = Query.Ctx.Basic(Instant.now())
  private val p = Page.Params.defaults
  private val titleFilter = new Table.Filter.Value("title", "Short title", false, f = str => POSTS.TITLE.like("%" + str + "%"))
  private val dateFilter = new Table.Filter.Bool("future", "Future", false, onTrue = ctx => POSTS.DATE.gt(ctx.now), onFalse = ctx => POSTS.DATE.lt(ctx.now))
  private val countFilter = new Table.Filter.Bool("count", "Count", true, onTrue = _ => AggField("COUNT(id)").gt(0), onFalse = _ => AggField("COUNT(id)").is(0))

  describe("Query") {
    describe("Insert") {
      it("should insert data in a table") {
        CATEGORIES.insert.values(Category.tech.id, Category.tech.name).sql shouldBe "INSERT INTO categories (id, name) VALUES (?, ?)"
      }
      it("should handle nullable columns with data") {
        USERS.insert.values(User.loic.id, User.loic.name, User.loic.email.get).sql shouldBe "INSERT INTO users (id, name, email) VALUES (?, ?, ?)"
      }
      it("should handle nullable columns with optionals") {
        // required because I expect a Put[A] in insert when A is Option[B] instead of Put[B], and this fail when having a None :(
        implicit def optPut[A: Put]: Put[Option[A]] = implicitly[Put[A]].contramap[Option[A]](_.get) // FIXME to remove

        USERS.insert.values(User.loic.id, User.loic.name, User.loic.email).sql shouldBe "INSERT INTO users (id, name, email) VALUES (?, ?, ?)"
      }
      it("should fail on bad argument number") {
        an[Exception] should be thrownBy CATEGORIES.insert.values(Category.tech.id)
      }
    }
    describe("Update") {

    }
    describe("Delete") {

    }
    describe("Select") {
      describe("page") {
        it("should build a paginated query") {
          USERS.select.page[User](p, ctx).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 20 OFFSET 0"
        }
        it("should change pagination based on page no and size") {
          USERS.select.page[User](p.page(3), ctx).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 20 OFFSET 40"
          USERS.select.page[User](p.pageSize(5), ctx).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 5 OFFSET 0"
          USERS.select.page[User](p.page(2).pageSize(10), ctx).sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 10 OFFSET 10"
        }
        describe("search") {
          it("should search on all fields when not specified") {
            USERS.select.page[User](p.search("q"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.id ILIKE ? OR u.name ILIKE ? OR u.email ILIKE ? LIMIT 20 OFFSET 0"
            USERS.select.where(_.ID lt User.Id(10)).page[User](p, ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.id < ? LIMIT 20 OFFSET 0"
            USERS.select.where(_.ID lt User.Id(10)).page[User](p.search("q"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE (u.id < ?) AND (u.id ILIKE ? OR u.name ILIKE ? OR u.email ILIKE ?) LIMIT 20 OFFSET 0"
          }
          it("should search on fields specified on table") {
            CATEGORIES.select.orderBy().page[Category](p.search("q"), ctx).sql shouldBe
              "SELECT c.id, c.name FROM categories c WHERE c.name ILIKE ? LIMIT 20 OFFSET 0"
          }
        }
        describe("order") {
          it("should handle custom order") {
            USERS.select.page[User](p, ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u LIMIT 20 OFFSET 0"
            USERS.select.page[User](p.orderBy("name", "-id"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name, u.id IS NULL, u.id DESC LIMIT 20 OFFSET 0"
          }
          it("should use table orders when exists") {
            val users = USERS.addSort(Table.Sort("test", USERS.NAME.asc, USERS.ID.desc))
            users.select.page[User](p.orderBy("test"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name, u.id IS NULL, u.id DESC LIMIT 20 OFFSET 0"
            users.select.page[User](p.orderBy("-test"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name DESC, u.id IS NULL, u.id LIMIT 20 OFFSET 0"
          }
          it("should ignore invalid orders") {
            USERS.select.page[User](p.orderBy("name", "toto"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u ORDER BY u.name IS NULL, u.name LIMIT 20 OFFSET 0"
          }
        }
        describe("filter") {
          it("should support boolean filters") {
            val users = USERS.filters(
              Table.Filter.Bool.fromNullable("email", "With email", USERS.EMAIL),
              new Table.Filter.Bool("dups", "Duplicates", aggregation = true, onTrue = _ => TableField("COUNT(u.id)").gt(1), onFalse = _ => TableField("COUNT(u.id)").is(1)))

            users.select.page[User](p.filters("email" -> "true"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.email IS NOT NULL LIMIT 20 OFFSET 0"
            users.select.fields(USERS.NAME, TableField("COUNT(u.id)").as("nb")).groupBy(USERS.NAME).page[(String, Long)](p.filters("dups" -> "true"), ctx).sql shouldBe
              "SELECT u.name, COUNT(u.id) as nb FROM users u GROUP BY u.name HAVING COUNT(u.id) > ? LIMIT 20 OFFSET 0"
            users.select.fields(USERS.NAME, TableField("COUNT(u.id)").as("nb")).groupBy(USERS.NAME).page[(String, Long)](p.filters("dups" -> "true", "email" -> "false"), ctx).sql shouldBe
              "SELECT u.name, COUNT(u.id) as nb FROM users u WHERE u.email IS NULL GROUP BY u.name HAVING COUNT(u.id) > ? LIMIT 20 OFFSET 0"
          }
          it("should support enum filters") {
            USERS
              .filters(Table.Filter.Enum.fromValues("name", "Name", USERS.NAME, List(("loic", "Loic", "loic"))))
              .select.page[User](p.filters("name" -> "loic"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.name=? LIMIT 20 OFFSET 0"
          }
          it("should support value filters") {
            USERS
              .filters(Table.Filter.Value.fromField("name", "Name", USERS.NAME))
              .select.page[User](p.filters("name" -> "loic,tim"), ctx).sql shouldBe
              "SELECT u.id, u.name, u.email FROM users u WHERE u.name ILIKE ? AND u.name ILIKE ? LIMIT 20 OFFSET 0"
          }
        }
      }
    }
    describe("Inner") {
      it("should compute the WHERE clause") {
        WhereClause(None, None, None).sql shouldBe ""
        WhereClause(Some(POSTS.CATEGORY.is(Category.Id(1))), None, None).sql shouldBe " WHERE p.category=?"
        WhereClause(Some(POSTS.CATEGORY.is(Category.Id(1)) and POSTS.AUTHOR.is(User.Id(1))), None, None).sql shouldBe " WHERE p.category=? AND p.author=?"
        WhereClause(None, Some((Page.Search("q"), List(POSTS.TITLE))), None).sql shouldBe " WHERE p.title ILIKE ?"
        WhereClause(None, Some((Page.Search("q"), List(POSTS.TITLE, POSTS.TEXT))), None).sql shouldBe " WHERE p.title ILIKE ? OR p.text ILIKE ?"
        WhereClause(None, None, Some((Map("future" -> "true"), List(dateFilter, titleFilter), ctx))).sql shouldBe " WHERE p.date > ?"
        WhereClause(None, None, Some((Map("future" -> "true", "title" -> "hello"), List(dateFilter, titleFilter), ctx))).sql shouldBe " WHERE (p.date > ?) AND (p.title LIKE ?)"

        WhereClause(Some(POSTS.CATEGORY.is(Category.Id(1))), Some((Page.Search("q"), List(POSTS.TITLE))), None).sql shouldBe " WHERE (p.category=?) AND (p.title ILIKE ?)"
        WhereClause(Some(POSTS.CATEGORY.is(Category.Id(1))), None, Some((Map("future" -> "true"), List(dateFilter), ctx))).sql shouldBe " WHERE (p.category=?) AND (p.date > ?)"
        WhereClause(None, Some((Page.Search("q"), List(POSTS.TITLE))), Some((Map("future" -> "true"), List(dateFilter), ctx))).sql shouldBe " WHERE (p.date > ?) AND (p.title ILIKE ?)"

        WhereClause(
          Some(POSTS.CATEGORY.is(Category.Id(1)) and POSTS.AUTHOR.is(User.Id(1))),
          Some((Page.Search("q"), List(POSTS.TITLE, POSTS.TEXT))),
          Some((Map("future" -> "true", "title" -> "hello"), List(dateFilter, titleFilter), ctx))
        ).sql shouldBe " WHERE (p.category=? AND p.author=?) AND ((p.date > ?) AND (p.title LIKE ?)) AND (p.title ILIKE ? OR p.text ILIKE ?)"
      }
      it("should compute the GROUP BY clause") {
        GroupByClause(List()).sql shouldBe ""
        GroupByClause(List(POSTS.ID)).sql shouldBe " GROUP BY p.id"
        GroupByClause(List(POSTS.ID, POSTS.TITLE)).sql shouldBe " GROUP BY p.id, p.title"
      }
      it("should compute the HAVING clause") {
        HavingClause(None, None).sql shouldBe ""
        HavingClause(Some(POSTS.CATEGORY.is(Category.Id(1))), None).sql shouldBe " HAVING p.category=?"
        HavingClause(Some(POSTS.CATEGORY.is(Category.Id(1)) and POSTS.AUTHOR.is(User.Id(1))), None).sql shouldBe " HAVING p.category=? AND p.author=?"
        HavingClause(None, Some((Map("count" -> "true"), List(countFilter), ctx))).sql shouldBe " HAVING COUNT(id) > ?"
      }
      it("should compute the ORDER BY clause") {
        OrderByClause(List(), None, nullsFirst = false).sql shouldBe ""
        OrderByClause(List(POSTS.ID.asc), None, nullsFirst = false).sql shouldBe " ORDER BY p.id IS NULL, p.id"
        OrderByClause(List(POSTS.ID.asc), Some((Page.OrderBy("title"), List(Table.Sort(POSTS.TITLE.desc)), List(POSTS.TITLE))), nullsFirst = false).sql shouldBe " ORDER BY p.title IS NULL, p.title DESC"
      }
      it("should compute the LIMIT clause") {
        LimitClause(None).sql shouldBe ""
        LimitClause(Some(Expr.Value(1))).sql shouldBe " LIMIT ?"
        LimitClause(Some(Expr.Floor(Expr.ValueField(POSTS.ID)) * Expr.Value(2))).sql shouldBe " LIMIT FLOOR(p.id) * ?"
      }
      it("should compute the OFFSET clause") {
        OffsetClause(None).sql shouldBe ""
        OffsetClause(Some(Expr.Value(1))).sql shouldBe " OFFSET ?"
        OffsetClause(Some(Expr.Floor(Expr.ValueField(POSTS.ID)) * Expr.Value(2))).sql shouldBe " OFFSET FLOOR(p.id) * ?"
      }
    }
  }
}
