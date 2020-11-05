package gospeak.libs.scala

import java.io.File
import java.nio.file.{Files, Paths}

import gospeak.libs.scala.Extensions._

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
    Try(Files.readAllBytes(Paths.get(path))).map(new String(_))

  def listFiles(path: String, recursively: Boolean = true): Try[List[String]] = Try {
    def listDir(dir: File): List[File] = {
      if (dir.isDirectory) {
        Option(dir.listFiles).map(_.toList.flatMap(f => if (recursively) listDir(f) else List(f))).getOrElse(List())
      } else {
        List(dir)
      }
    }

    listDir(new File(path)).filter(_.isFile).map(_.getPath).sorted
  }

  // return a map for files with their relative path inside the directory and their content
  def getDirContent(path: String): Try[Map[String, String]] = {
    FileUtils.listFiles(path)
      .flatMap(_.map(p => FileUtils.read(p).map(c => (p.stripPrefix(path + "/"), c))).sequence)
      .map(_.toMap)
  }

  def deleteFile(path: String): Try[Unit] =
    Try(Files.delete(Paths.get(path)))

  def delete(path: String): Try[Unit] = Try {
    def deleteDir(dir: File): Boolean = {
      if (dir.isDirectory) {
        Option(dir.listFiles).foreach(_.foreach(deleteDir))
      }
      dir.delete
    }

    deleteDir(new File(path))
  }

  def curPath: String = new java.io.File(".").getCanonicalPath

  // remove the first folder if it does not exist as IntelliJ uses project home and sbt uses module home to run tests :(
  def adaptLocalPath(path: String): String = {
    val base = path.split('/').head
    if (FileUtils.exists(base)) path else path.split('/').drop(1).mkString("/")
  }
}
