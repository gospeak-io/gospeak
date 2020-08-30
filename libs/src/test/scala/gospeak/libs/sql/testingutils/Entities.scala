package gospeak.libs.sql.testingutils

import java.time.{Instant, LocalDate}

object Entities {

  case class User(id: User.Id,
                  name: String,
                  email: String)

  object User {

    case class Id(value: Int) extends AnyVal

    val loic: User = User(Id(1), "loic", "loic@mail.com")
    val jean: User = User(Id(2), "jean", null)
    val tim: User = User(Id(3), "tim", "tim@mail.com")
  }

  case class Category(id: Category.Id,
                      name: String)

  object Category {

    case class Id(value: Int) extends AnyVal

    val tech: Category = Category(Id(1), "Tech")
    val political: Category = Category(Id(2), "Political")
  }

  case class Post(id: Post.Id,
                  title: String,
                  text: String,
                  date: Instant,
                  author: Int,
                  category: Option[Int])

  object Post {

    case class Id(value: Int) extends AnyVal

    val newYear: Post = Post(Id(1), "Happy new year", "The awful year", Instant.ofEpochSecond(1577833140), 1, None)
    val first2020: Post = Post(Id(2), "First 2020 post", "bla bla", Instant.ofEpochSecond(1577876400), 1, None)
    val sqlQueries: Post = Post(Id(3), "SQL Queries", "Using jOOQ and Doobie", Instant.ofEpochSecond(1595082720), 2, Some(1))
  }

  case class Kind(char: String, varchar: String, timestamp: Instant, date: LocalDate, boolean: Boolean, int: Int, bigint: Long, double: Double, a_long_name: Int)

  object Kind {
    val one: Kind = Kind("char", "varchar", Instant.ofEpochSecond(1596615600), LocalDate.of(2020, 8, 5), boolean = true, 1, 10, 4.5, 0)
  }

}
