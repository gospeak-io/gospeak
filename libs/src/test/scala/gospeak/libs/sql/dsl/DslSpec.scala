package gospeak.libs.sql.dsl

import java.time.Instant

import doobie.syntax.string._
import doobie.util.Put
import doobie.util.meta.Meta
import gospeak.libs.scala.domain.Page
import gospeak.libs.sql.testingutils.Entities.{Category, Post, User}
import gospeak.libs.sql.testingutils.SqlSpec
import gospeak.libs.sql.testingutils.database.Tables._

/**
 * TODO :
 *  - remove Put[Option[A]], use shapeless ?
 *  - rename `Builder.withFields` to `fields` (polymorphism problem...)
 *  - keep typed fields after joins (in JoinTable)
 */
class DslSpec extends SqlSpec {
  // required because I expect a Put[A] in insert when A is Option[B] instead of Put[B], and this fail when having a None :(
  private implicit def optPut[A: Put]: Put[Option[A]] = implicitly[Put[A]].contramap[Option[A]](_.get) // FIXME to remove
  private implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  private val ctx: Query.Ctx = Query.Ctx.Basic(Instant.now())
  private val params = Page.Params(Page.No(1), Page.Size(2))

  describe("Table") {
    it("should build CRUD operations") {
      val u = User(User.Id(4), "Lou", Some("lou@mail.com"))
      val u2 = u.copy(name = "Test", email = Some("test@mail.com"))
      val select = USERS.select.where(_.ID is u.id).option[User]
      val insert = USERS.insert.values(u.id, u.name, u.email)
      val update = USERS.update.set(_.NAME, u2.name).set(_.EMAIL, u2.email).where(_.ID is u.id)
      val delete = USERS.delete.where(_.ID is u.id)

      select.sql shouldBe "SELECT u.id, u.name, u.email FROM users u WHERE u.id=?"
      insert.sql shouldBe "INSERT INTO users (id, name, email) VALUES (?, ?, ?)"
      update.sql shouldBe "UPDATE users u SET name=?, email=? WHERE u.id=?"
      delete.sql shouldBe "DELETE FROM users u WHERE u.id=?"

      select.run(xa).unsafeRunSync() shouldBe None
      insert.run(xa).unsafeRunSync()
      select.run(xa).unsafeRunSync() shouldBe Some(u)
      update.run(xa).unsafeRunSync()
      select.run(xa).unsafeRunSync() shouldBe Some(u2)
      delete.run(xa).unsafeRunSync()
      select.run(xa).unsafeRunSync() shouldBe None
    }
    it("should add default sort on select") {
      val select = CATEGORIES.select.all[Category]
      select.sql shouldBe "SELECT c.id, c.name FROM categories c ORDER BY c.name IS NULL, c.name DESC, c.id IS NULL, c.id"
    }
    describe("pagination") {
      it("should build paginated select") {
        val select = USERS.select.page[User](params, ctx)
        select.sql shouldBe "SELECT u.id, u.name, u.email FROM users u LIMIT 2 OFFSET 0"
        select.countFr.query[Long].sql shouldBe "SELECT COUNT(*) FROM (SELECT u.id FROM users u) as cnt"

        val userPage = select.run(xa).unsafeRunSync()
        userPage.items shouldBe List(User.loic, User.jean)
        userPage.params shouldBe params
        userPage.total.value shouldBe 3
      }
    }
    describe("joins") {
      it("should build a joined select query") {
        val joined = POSTS.join(USERS).on(_.AUTHOR is _.ID).join(CATEGORIES, _.LeftOuter).on(POSTS.CATEGORY is _.ID)
        val res = joined.select.fields(POSTS.getFields).where(USERS.ID is User.loic.id).all[Post]
        val exp = fr0"SELECT p.id, p.title, p.text, p.date, p.author, p.category FROM posts p " ++
          fr0"INNER JOIN users u ON p.author=u.id " ++
          fr0"LEFT OUTER JOIN categories c ON p.category=c.id " ++
          fr0"WHERE u.id=${User.loic.id}"
        res.sql shouldBe exp.query[Post].sql
        res.run(xa).unsafeRunSync() shouldBe List(Post.newYear, Post.first2020)
      }
      it("should have auto joins") {
        val join = POSTS.join(USERS).on(_.AUTHOR is _.ID)
        val autoJoin = POSTS.joinOn(_.AUTHOR)
        autoJoin shouldBe join
      }
      it("should join using field refs") {
        val basicJoin = POSTS.join(USERS).on(_.AUTHOR is _.ID).join(CATEGORIES, _.LeftOuter).on(POSTS.CATEGORY is _.ID)
        val fieldJoin = POSTS.joinOn(POSTS.AUTHOR).joinOn(POSTS.CATEGORY, _.LeftOuter)
        fieldJoin shouldBe basicJoin
      }
    }
    describe("unions") {
      it("should build a union table") {
        val users = USERS.select.withFields(_.ID, _.NAME, _ => TableField[String]("'user'", alias = Some("kind")))
        val cats = CATEGORIES.select.appendFields(TableField[String]("'category'", alias = Some("kind"))).orderBy()
        val union = users.union(cats, alias = Some("e"), sorts = List(("kind", "kind", List("-kind", "id"))))
        val res = union.select.where(_.id[Int].is(1)).all[(Int, String, String)]
        val exp = fr0"SELECT e.id, e.name, e.kind " ++
          fr0"FROM ((SELECT u.id, u.name, 'user' as kind FROM users u) UNION (SELECT c.id, c.name, 'category' as kind FROM categories c)) e " ++
          fr0"WHERE e.id=? ORDER BY e.kind IS NULL, e.kind DESC, e.id IS NULL, e.id"
        res.sql shouldBe exp.query[(Int, String, String)].sql
        res.run(xa).unsafeRunSync() shouldBe List(
          (User.loic.id.value, User.loic.name, "user"),
          (Category.tech.id.value, Category.tech.name, "category"))

        union.select.where(_.field[Int]("id").is(1)).all[(Int, String, String)] shouldBe res
        union.select.where(TableField[Int]("id", Some("e")).is(1)).all[(Int, String, String)] shouldBe res
      }
      it("should fail on not matching field count") {
        an[Exception] should be thrownBy USERS.select.union(CATEGORIES.select)
      }
      it("should fail when field names do not match") {
        an[Exception] should be thrownBy USERS.select.withFields(_.ID, _.EMAIL).union(CATEGORIES.select)
        USERS.select.withFields(_.ID, _.EMAIL.as("name")).union(CATEGORIES.select)
      }
      it("should fail when sorts do not have fields or unknown fields") {
        an[Exception] should be thrownBy USERS.select.withFields(_.ID, _.NAME).union(CATEGORIES.select, sorts = List(("s", "s", List())))
        an[Exception] should be thrownBy USERS.select.withFields(_.ID, _.NAME).union(CATEGORIES.select, sorts = List(("s", "s", List("email"))))
        USERS.select.withFields(_.ID, _.NAME).union(CATEGORIES.select, sorts = List(("s", "s", List("name"))))
      }
    }
    describe("query validations") {
      it("should check that used fields belong to tables") {
        an[Exception] should be thrownBy USERS.joinOn(POSTS.CATEGORY)
        an[Exception] should be thrownBy POSTS.joinOn(POSTS.AUTHOR).fields(CATEGORIES.ID)
        an[Exception] should be thrownBy USERS.select.fields(POSTS.CATEGORY)
        an[Exception] should be thrownBy USERS.select.where(POSTS.CATEGORY.isNull)
        an[Exception] should be thrownBy USERS.select.groupBy(POSTS.CATEGORY)
        an[Exception] should be thrownBy USERS.select.orderBy(POSTS.CATEGORY.asc)
      }
    }
  }
}
