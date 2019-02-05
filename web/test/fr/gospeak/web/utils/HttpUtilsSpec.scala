package fr.gospeak.web.utils

import fr.gospeak.web.utils.HttpUtils._
import org.scalatest.{FunSpec, Matchers}

class HttpUtilsSpec extends FunSpec with Matchers {
  describe("HttpUtils") {
    describe("getUrlPath") {
      it("should return the path part of an url") {
        val url = "http://localhost:9000/u/groups/ht-paris/events/2019-02/edit?toto=test#aaa"
        getUrlPath(url) shouldBe "/u/groups/ht-paris/events/2019-02/edit"
      }
    }
    describe("getUriPath") {
      it("should return the path part of an uri") {
        val url = "/u/groups/ht-paris/events/2019-02/edit?toto=test#aaa"
        getUriPath(url) shouldBe "/u/groups/ht-paris/events/2019-02/edit"
      }
    }
  }
}
