package fr.gospeak.core.domain

import java.util.UUID

import scala.util.{Failure, Success, Try}

abstract class DataClass(value: String) {
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
