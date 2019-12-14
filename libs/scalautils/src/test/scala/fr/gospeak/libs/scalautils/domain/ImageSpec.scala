package fr.gospeak.libs.scalautils.domain

import org.scalatest.{FunSpec, Matchers}

class ImageSpec extends FunSpec with Matchers {
  describe("Image") {
    describe("cloudinary integration") {
      it("should parse a cloudinary url") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/v1576350888/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        Image.parseCloudinaryUrl(url).get shouldBe Image.CloudinaryUrl(
          cloudName = "dev-gospeak",
          transformations = Seq(),
          version = Some("v1576350888"),
          publicId = "users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar",
          format = "png")
      }
      it("should work with urls containing transformations") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/c_limit,h_60,w_90/v1576350888/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        Image.parseCloudinaryUrl(url).get shouldBe Image.CloudinaryUrl(
          cloudName = "dev-gospeak",
          transformations = Seq(Seq("c_limit", "h_60", "w_90")),
          version = Some("v1576350888"),
          publicId = "users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar",
          format = "png")
      }
      it("should work with urls containing multiple transformations") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/h_200/c_limit,h_60,w_90/v1576350888/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        Image.parseCloudinaryUrl(url).get shouldBe Image.CloudinaryUrl(
          cloudName = "dev-gospeak",
          transformations = Seq(Seq("h_200"), Seq("c_limit", "h_60", "w_90")),
          version = Some("v1576350888"),
          publicId = "users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar",
          format = "png")
      }
    }
  }
}
