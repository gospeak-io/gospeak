package gospeak.libs.sql.generator

import cats.effect.IO
import gospeak.libs.scala.Extensions._
import gospeak.libs.sql.generator.reader.Reader
import gospeak.libs.sql.generator.writer.Writer

object Generator {
  def generate(xa: doobie.Transactor[IO], reader: Reader, writer: Writer): IO[Unit] = for {
    database <- reader.read(xa)
    _ <- writer.write(database).toIO
  } yield ()
}
