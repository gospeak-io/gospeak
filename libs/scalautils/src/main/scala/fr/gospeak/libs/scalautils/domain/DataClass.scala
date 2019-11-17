package fr.gospeak.libs.scalautils.domain

import java.util.UUID

import fr.gospeak.libs.scalautils.Extensions._

import scala.util.Try
import scala.util.matching.Regex

/**
  * Like a single value case class but allows private constructor and no generated apply
  */
abstract class DataClass(val value: String) {
  def canEqual(other: Any): Boolean = other.isInstanceOf[DataClass]

  override def equals(other: Any): Boolean = other match {
    case that: DataClass =>
      (that canEqual this) &&
        value == that.value
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(value)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString: String = value
}

trait IId {
  val value: String
}

abstract class UuidIdBuilder[A <: IId](clazz: String, build: String => A) {
  def generate(): A = build(UUID.randomUUID().toString)

  def from(in: String): Either[CustomException, A] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(build(in))
    else Left(CustomException(s"'$in' is an invalid $clazz", errs))
  }

  private[domain] def errors(in: String): Seq[CustomError] = {
    val tryUuid = Try(UUID.fromString(in))
    Seq(
      tryUuid.failed.map(_.getMessage).toOption
    ).flatten.map(CustomError)
  }
}

trait ISlug {
  val value: String
}

abstract class SlugBuilder[A <: ISlug](clazz: String, build: String => A) {

  import SlugBuilder._

  def from(in: String): Either[CustomException, A] = {
    val errs = errors(in)
    if (errs.isEmpty) Right(build(in))
    else Left(CustomException(s"'$in' is an invalid $clazz", errs))
  }

  private[domain] def errors(in: String): Seq[CustomError] =
    Seq(
      if (in.length > maxLength) Some(s"$clazz should not exceed $maxLength chars") else None,
      in match {
        case pattern() => None
        case _ => Some(s"do not match pattern $pattern")
      }
    ).flatten.map(CustomError)
}

object SlugBuilder {
  val maxLength: Int = Values.maxLength.title
  val pattern: Regex = "[a-z0-9-_]+".r
}

trait StringEnum extends Product with Serializable {
  def value: String
}

abstract class EnumBuilder[A <: StringEnum](clazz: String) {
  val all: Seq[A]

  def from(str: String): Either[CustomException, A] =
    all.find(_.value == str).toEither(CustomException(s"'$str' in not a valid $clazz"))
}
