package gospeak.libs.sql.testingutils.database.tables

import java.time.Instant

import gospeak.libs.sql.dsl._

/**
 * Class generated by gospeak.libs.sql.generator.writer.ScalaWriter
 */
class POSTS private() extends Table.SqlTable("PUBLIC", "posts", None) {
  val ID: Field[gospeak.libs.sql.testingutils.Entities.Post.Id, POSTS] = new Field[gospeak.libs.sql.testingutils.Entities.Post.Id, POSTS](this, "id") // INT NOT NULL
  val TITLE: Field[String, POSTS] = new Field[String, POSTS](this, "title") // VARCHAR(50) NOT NULL
  val TEXT: Field[String, POSTS] = new Field[String, POSTS](this, "text") // VARCHAR(4096) NOT NULL
  val DATE: Field[Instant, POSTS] = new Field[Instant, POSTS](this, "date") // TIMESTAMP NOT NULL
  val AUTHOR: FieldRef[gospeak.libs.sql.testingutils.Entities.User.Id, POSTS, USERS] = new FieldRef[gospeak.libs.sql.testingutils.Entities.User.Id, POSTS, USERS](this, "author", USERS.table.ID) // INT NOT NULL
  val CATEGORY: FieldRef[gospeak.libs.sql.testingutils.Entities.Category.Id, POSTS, CATEGORIES] = new FieldRef[gospeak.libs.sql.testingutils.Entities.Category.Id, POSTS, CATEGORIES](this, "category", CATEGORIES.table.ID) // INT

  override def getFields: List[Field[_, POSTS]] = List(ID, TITLE, TEXT, DATE, AUTHOR, CATEGORY)

  def AUTHORJoin: Table.JoinTable = join(USERS.table, AUTHOR.is(USERS.table.ID))

  def CATEGORYJoin: Table.JoinTable = join(CATEGORIES.table, CATEGORY.is(CATEGORIES.table.ID))
}

private[database] object POSTS {
  val table = new POSTS() // unique table instance, should be accessed through `gospeak.libs.sql.testingutils.database.Tables` object
}
