package fr.gospeak.libs.scalautils

import java.nio.file.{Files, Paths}

import scala.collection.JavaConverters._
import scala.util.Try

object FileUtils {
  def parent(path: String): String =
    path.split("/").dropRight(1).mkString("/")

  def exists(path: String): Boolean =
    Files.exists(Paths.get(path))

  def mkdirs(path: String): Try[Unit] =
    Try(Files.createDirectories(Paths.get(path)))

  def write(path: String, content: String): Try[Unit] =
    mkdirs(parent(path)).flatMap(_ => Try(Files.write(Paths.get(path), content.getBytes)))

  def read(path: String): Try[String] =
    Try(Files.readAllLines(Paths.get(path))).map(_.asScala.mkString("\n"))

  def delete(path: String): Try[Unit] =
    Try(Files.delete(Paths.get(path)))
}
