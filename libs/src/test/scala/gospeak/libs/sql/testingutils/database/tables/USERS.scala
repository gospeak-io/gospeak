package gospeak.libs.sql.testingutils.database.tables

import gospeak.libs.sql.testingutils.Entities._

import gospeak.libs.sql.dsl._

/**
 * Class generated by gospeak.libs.sql.generator.writer.ScalaWriter
 */
class USERS private() extends Table.SqlTable("PUBLIC", "users", Some("u")) {
  val ID: Field[User.Id, USERS] = new Field[User.Id, USERS](this, "id") // INT NOT NULL
  val NAME: Field[String, USERS] = new Field[String, USERS](this, "name") // VARCHAR(50) NOT NULL
  val EMAIL: Field[Option[String], USERS] = new Field[Option[String], USERS](this, "email") // VARCHAR(50)

  override def getFields: List[Field[_, USERS]] = List(ID, NAME, EMAIL)
}

private[database] object USERS {
  val table = new USERS() // unique table instance, should be accessed through `gospeak.libs.sql.testingutils.database.Tables` object
}
