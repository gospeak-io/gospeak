package gospeak.libs.scala

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

  // remove the first folder if it does not exist as IntelliJ uses project home and sbt uses module home to run tests :(
  def adaptLocalPath(path: String): String = {
    val base = path.split('/').head
    if (FileUtils.exists(base)) path else path.split('/').drop(1).mkString("/")
  }
}
