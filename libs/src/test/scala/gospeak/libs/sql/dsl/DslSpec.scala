package gospeak.libs.sql.dsl

import doobie.implicits._
import gospeak.libs.sql.testingutils.Entities.{Post, User}
import gospeak.libs.sql.testingutils.SqlSpec
import gospeak.libs.sql.testingutils.database.Tables._

class DslSpec extends SqlSpec {
  describe("Table") {
    it("should allow simple queries") {
      val id = 1
      val res = USERS.select.where(USERS.ID.is(id)).as[User]
      val exp = fr0"SELECT users.id, users.name, users.email FROM users WHERE users.id=$id"
      res.fr.query.sql shouldBe exp.query.sql
    }
    it("should build a joined query") {
      val id = 1
      val joined = POSTS.join(USERS, POSTS.AUTHOR.is(USERS.ID)).joinOpt(CATEGORIES, POSTS.CATEGORY.is(CATEGORIES.ID))
      val res = joined.select.fields(POSTS.getFields).where(USERS.ID.is(id)).as[Post]
      val exp = fr0"SELECT posts.id, posts.title, posts.text, posts.date, posts.author, posts.category FROM posts " ++
        fr0"INNER JOIN users ON posts.author=users.id " ++
        fr0"LEFT OUTER JOIN categories ON posts.category=categories.id " ++
        fr0"WHERE users.id=$id"
      res.fr.query.sql shouldBe exp.query.sql
    }
  }
}
