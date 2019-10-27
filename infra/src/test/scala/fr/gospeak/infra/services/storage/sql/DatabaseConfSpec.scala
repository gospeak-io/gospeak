package fr.gospeak.infra.services.storage.sql

import fr.gospeak.libs.scalautils.domain.Secret
import org.scalatest.{FunSpec, Matchers}

class DatabaseConfSpec extends FunSpec with Matchers {
  describe("DatabaseConf") {
    describe("from") {
      it("should parse a H2 url") {
        val url = "jdbc:h2:mem:gospeak_db;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
        DatabaseConf.from(url) shouldBe Right(DatabaseConf.H2(url))
      }
      it("should parse a PostgreSQL url") {
        val url = "postgres://ugmagymioprjcz:4eece8951709fdd97f7de69aeb812692a937d11aa32fd482cda27a0fab0eebb1@ec2-42-149-162-364.eu-west-1.compute.amazonaws.com:5432/bid2dp7shda753"
        DatabaseConf.from(url) shouldBe Right(DatabaseConf.PostgreSQL(
          "jdbc:postgresql://ec2-42-149-162-364.eu-west-1.compute.amazonaws.com:5432/bid2dp7shda753",
          "ugmagymioprjcz",
          Secret("4eece8951709fdd97f7de69aeb812692a937d11aa32fd482cda27a0fab0eebb1")))
      }
    }
  }
}
