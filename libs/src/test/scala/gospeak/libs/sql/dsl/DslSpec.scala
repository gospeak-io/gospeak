package gospeak.libs.sql.dsl

import doobie.implicits._
import gospeak.libs.sql.testingutils.Entities.{Post, User}
import gospeak.libs.sql.testingutils.SqlSpec
import gospeak.libs.sql.testingutils.database.Tables._

class DslSpec extends SqlSpec {
  describe("Table") {
    it("should allow simple queries") {
      val res = USERS.select.where(_.ID eq User.loic.id).as[User]
      val exp = fr0"SELECT u.id, u.name, u.email FROM users u WHERE u.id=${User.loic.id}"
      res.fr.query.sql shouldBe exp.query.sql
      res.runOption(xa).unsafeRunSync() shouldBe Some(User.loic)
    }
    it("should build a joined query") {
      val joined = POSTS.join(USERS).on(_.AUTHOR eq _.ID).joinOpt(CATEGORIES).on(POSTS.CATEGORY eq _.ID)
      val res = joined.select.fields(POSTS.getFields).where(USERS.ID eq User.loic.id).as[Post]
      val exp = fr0"SELECT p.id, p.title, p.text, p.date, p.author, p.category FROM posts p " ++
        fr0"INNER JOIN users u ON p.author=u.id " ++
        fr0"LEFT OUTER JOIN categories c ON p.category=c.id " ++
        fr0"WHERE u.id=${User.loic.id}"
      res.fr.query.sql shouldBe exp.query.sql
      res.runList(xa).unsafeRunSync() shouldBe List(Post.newYear, Post.first2020)
    }
  }
}
