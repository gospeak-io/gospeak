package gospeak.web.utils

import gospeak.web.testingutils.BaseSpec
import play.api.libs.json._

class JsonUtilsSpec extends BaseSpec {
  private val json1 = Json.obj(
    "id" -> JsNumber(1),
    "name" -> JsString("Loïc"),
    "admin" -> JsBoolean(true),
    "rights" -> Json.arr(
      Json.obj("id" -> JsNumber(3), "level" -> JsString("high")),
      Json.obj("id" -> JsNumber(4), "level" -> JsString("low")),
    )
  )
  private val json2 = Json.obj(
    "id" -> JsNumber(2),
    "name" -> JsNumber(5),
    "user" -> JsBoolean(false),
    "rights" -> Json.arr(
      Json.obj("id" -> JsNumber(2), "level" -> JsString("low")),
      Json.obj("id" -> JsNumber(4)),
      Json.obj("id" -> JsNumber(5)),
    )
  )

  describe("JsonUtils") {
    describe("diff") {
      it("should perform a diff") {
        JsonUtils.diff(json1, json1) shouldBe Seq()
        JsonUtils.diff(json1, json2) shouldBe Seq(
          (JsPath \ "admin", Some(JsBoolean(true)), None),
          (JsPath \ "id", Some(JsNumber(1)), Some(JsNumber(2))),
          (JsPath \ "name", Some(JsString("Loïc")), Some(JsNumber(5))),
          (JsPath \ "rights" \ 0 \ "id", Some(JsNumber(3)), Some(JsNumber(2))),
          (JsPath \ "rights" \ 0 \ "level", Some(JsString("high")), Some(JsString("low"))),
          (JsPath \ "rights" \ 1 \ "level", Some(JsString("low")), None),
          (JsPath \ "rights" \ 2, None, Some(Json.obj("id" -> JsNumber(5)))),
          (JsPath \ "user", None, Some(JsBoolean(false))))
      }
    }
  }
}
