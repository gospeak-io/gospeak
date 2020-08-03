package gospeak.libs.googlemaps

import gospeak.libs.scala.domain.Geo
import gospeak.libs.testingutils.BaseSpec

class GoogleMapsClientSpec extends BaseSpec {
  private val client = GoogleMapsClient.create("...")

  ignore("GooglePlaceClient") {
    it("should search for cities") {
      val places = client.search("Paris, France", Geo(48.86, 2.34)).unsafeRunSync()
      println(s"${places.length} places")
      places.foreach(p => println(s"  - $p"))
      places.length shouldBe 1
      places.head.id shouldBe "ChIJD7fiBh9u5kcRYJSMaMOCCwQ"
    }
    it("should fetch place details") {
      val place = client.getPlace("ChIJD7fiBh9u5kcRYJSMaMOCCwQ").unsafeRunSync()
      println(s"place: $place")
      place.name shouldBe "Paris"
      place.locality shouldBe Some("Paris")
    }
  }
}
