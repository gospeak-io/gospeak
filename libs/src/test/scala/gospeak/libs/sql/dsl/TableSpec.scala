package gospeak.libs.sql.dsl

import gospeak.libs.sql.dsl.Exceptions.NotImplementedJoin
import gospeak.libs.sql.testingutils.database.Tables.{CATEGORIES, FEATURED, POSTS, USERS}
import gospeak.libs.testingutils.BaseSpec

class TableSpec extends BaseSpec {
  describe("Table") {
    describe("join") {
      it("should join two sql tables") {
        POSTS.join(USERS).on(POSTS.AUTHOR.is(USERS.ID)).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
        POSTS.join(USERS).on(POSTS.AUTHOR is USERS.ID).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
        POSTS.join(USERS).on(POSTS.AUTHOR is _.ID).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
        POSTS.join(USERS).on(_.AUTHOR is _.ID).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
      }
      it("should specify join kind") {
        POSTS.join(USERS, _.LeftOuter).on(POSTS.AUTHOR.is(USERS.ID)).sql shouldBe "posts p LEFT OUTER JOIN users u ON p.author=u.id"
      }
      it("should join all kind of tables") {
        val POSTS_WITH_CATEGORIES = POSTS.join(CATEGORIES).on(_.CATEGORY is _.ID)
        val FEATURED_WITH_USERS = FEATURED.join(USERS).on(_.BY is _.ID)
        val COMMON_NAMES = USERS.select.withFields(_.NAME).union(CATEGORIES.select.withFields(_.NAME).orderBy())

        POSTS_WITH_CATEGORIES.sql shouldBe "posts p INNER JOIN categories c ON p.category=c.id"
        POSTS_WITH_CATEGORIES.join(USERS).on(POSTS.AUTHOR is _.ID).sql shouldBe "posts p INNER JOIN categories c ON p.category=c.id INNER JOIN users u ON p.author=u.id"
        FEATURED.join(POSTS_WITH_CATEGORIES).on((f, _) => f.POST_ID is POSTS.ID).sql shouldBe "featured INNER JOIN posts p ON featured.post_id=p.id INNER JOIN categories c ON p.category=c.id"
        FEATURED_WITH_USERS.join(POSTS_WITH_CATEGORIES).on(FEATURED.POST_ID is POSTS.ID).sql shouldBe "featured INNER JOIN users u ON featured.by=u.id INNER JOIN posts p ON featured.post_id=p.id INNER JOIN categories c ON p.category=c.id"
        an[NotImplementedJoin[_, _]] should be thrownBy USERS.join(COMMON_NAMES).on(_.NAME is _.name[String])
        an[NotImplementedJoin[_, _]] should be thrownBy FEATURED_WITH_USERS.join(COMMON_NAMES).on(_.name[String] is _.name[String])
        an[NotImplementedJoin[_, _]] should be thrownBy COMMON_NAMES.join(USERS).on(_.name[String] is _.NAME)
        an[NotImplementedJoin[_, _]] should be thrownBy COMMON_NAMES.join(FEATURED_WITH_USERS).on(_.name[String] is _.name[String])
        an[NotImplementedJoin[_, _]] should be thrownBy COMMON_NAMES.join(COMMON_NAMES).on(_.name[String] is _.name[String])
      }
      describe("auto") {
        it("should automatically join tables using ref fields") {
          POSTS.joinOn(_.AUTHOR, _.Inner).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
        }
        it("should perform auto joins with incoming ref field") {
          USERS.joinOn(POSTS.AUTHOR, _.Inner).sql shouldBe "users u INNER JOIN posts p ON u.id=p.author"
        }
        it("should choose auto join based on field kind") {
          POSTS.joinOn(POSTS.AUTHOR).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
          POSTS.joinOn(POSTS.CATEGORY).sql shouldBe "posts p LEFT OUTER JOIN categories c ON p.category=c.id"
          POSTS.joinOn(_.AUTHOR).sql shouldBe "posts p INNER JOIN users u ON p.author=u.id"
          POSTS.joinOn(_.CATEGORY).sql shouldBe "posts p LEFT OUTER JOIN categories c ON p.category=c.id"
        }
      }
    }
  }
}
