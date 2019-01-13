package fr.gospeak.core.domain.utils

import java.util.UUID

import scala.util.{Failure, Success, Try}

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

abstract class UuidIdBuilder[T](clazz: String, build: String => T) {
  def generate(): T = build(UUID.randomUUID().toString)

  def from(in: String): Try[T] = {
    val errs = errors(in)
    if (errs.isEmpty) Success(build(in))
    else Failure(new Exception(s"'$in' is an invalid $clazz: " + errs.mkString(", ")))
  }

  def errors(in: String): Seq[String] = {
    val tryUuid = Try(UUID.fromString(in))
    Seq(
      tryUuid.failed.map(_.getMessage).toOption
    ).flatten
  }
}
