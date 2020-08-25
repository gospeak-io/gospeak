package gospeak.libs.sql.generator.reader

import cats.effect.IO
import gospeak.libs.sql.generator.Database

trait Reader {
  def read(xa: doobie.Transactor[IO]): IO[Database]
}
