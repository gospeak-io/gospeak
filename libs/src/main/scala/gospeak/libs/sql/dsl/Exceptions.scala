package gospeak.libs.sql.dsl

import cats.data.NonEmptyList
import doobie.util.Read
import doobie.util.fragment.Fragment

object Exceptions {
  def check[A, T <: Table](cond: Cond, table: T, value: => A): A =
    NonEmptyList.fromList(cond.getFields.filterNot(table.has)).map(unknownFields => throw UnknownTableFields(table, unknownFields)).getOrElse(value)

  def check[T <: Table](cond: Cond, table: T): T = check(cond, table, table)

  case class UnknownTableFields[T <: Table](table: T, unknownFields: NonEmptyList[Field[_, Table.SqlTable]]) extends Exception(s"Fields ${unknownFields.toList.map(_.value).mkString(", ")} do not belong to the table") // TODO improve message with sql tables

  case class InvalidNumberOfValues[T <: Table.SqlTable](table: T, expectedLength: Int) extends Exception(s"Insert expects ${table.getFields.length} fields but got $expectedLength")

  case class InvalidNumberOfFields[A](read: Read[A], fields: List[Field[_, Table.SqlTable]]) extends Exception(s"Expects ${read.length} fields but got ${fields.length}")

  case class FailedQuery(fr: Fragment, cause: Throwable) extends Exception(s"Fail on ${fr.query.sql}: ${cause.getMessage}", cause)

}
