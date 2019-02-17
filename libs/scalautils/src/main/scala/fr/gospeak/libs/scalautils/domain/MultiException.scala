package fr.gospeak.libs.scalautils.domain

import cats.data.NonEmptyList

case class MultiException(errs: NonEmptyList[Throwable]) extends RuntimeException {
  override def getMessage: String =
    "Multi exception:\n" +
      errs.toList.map(e => s"- ${e.getMessage}").mkString("\n")
}
