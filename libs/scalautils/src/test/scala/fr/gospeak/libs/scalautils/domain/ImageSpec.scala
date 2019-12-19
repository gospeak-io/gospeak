package fr.gospeak.libs.scalautils.domain

import org.scalatest.{FunSpec, Matchers}

class ImageSpec extends FunSpec with Matchers {
  describe("Image") {
    describe("CloudinaryUrl") {
      val baseUrl = Image.CloudinaryUrl(
        cloudName = "dev-gospeak",
        resource = "image",
        kind = "upload",
        transformations = Seq(),
        version = None,
        publicId = "users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar",
        format = "png")

      it("should parse and format a cloudinary url") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        val res = baseUrl
        Image.CloudinaryUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
      it("should parse and format a cloudinary url with version") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/v1576350888/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        val res = baseUrl.copy(version = Some(1576350888))
        Image.CloudinaryUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
      it("should parse and format a cloudinary url with transformations") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/c_limit,h_60,w_90/v1576350888/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        val res = baseUrl.copy(version = Some(1576350888), transformations = Seq(Seq("c_limit", "h_60", "w_90")))
        Image.CloudinaryUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
      it("should parse and format a cloudinary url with multiple transformations") {
        val url = "https://res.cloudinary.com/dev-gospeak/image/upload/h_200,c_scale/c_limit,h_60,w_90/v1576350888/users/eba3d19a-8cd5-4f3d-b148-8783c8ec1a4f/avatar.png"
        val res = baseUrl.copy(version = Some(1576350888), transformations = Seq(Seq("h_200", "c_scale"), Seq("c_limit", "h_60", "w_90")))
        Image.CloudinaryUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
    }
    describe("AdorableUrl") {
      it("should parse and format an adorable url") {
        val url = "https://api.adorable.io/avatars/loic-knuchel.png"
        val res = Image.AdorableUrl(
          hash = "loic-knuchel",
          size = None)
        Image.AdorableUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
      it("should parse and format an adorable url with size") {
        val url = "https://api.adorable.io/avatars/285/loic-knuchel.png"
        val res = Image.AdorableUrl(
          hash = "loic-knuchel",
          size = Some(285))
        Image.AdorableUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
    }
    describe("GravatarUrl") {
      it("should parse and format a gravatar url") {
        val url = "https://secure.gravatar.com/avatar/9a2ae971f81eaff1f0da3d56cbf4a2ee"
        val res = Image.GravatarUrl(
          hash = "9a2ae971f81eaff1f0da3d56cbf4a2ee",
          params = Seq())
        Image.GravatarUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
      it("should parse and format a gravatar url with size and default") {
        val url = "https://secure.gravatar.com/avatar/9a2ae971f81eaff1f0da3d56cbf4a2ee?size=100&default=wavatar"
        val res = Image.GravatarUrl(
          hash = "9a2ae971f81eaff1f0da3d56cbf4a2ee",
          params = Seq("size" -> "100", "default" -> "wavatar"))
        Image.GravatarUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
      it("should parse and format a gravatar url with size and default as url") {
        val url = "https://secure.gravatar.com/avatar/9a2ae971f81eaff1f0da3d56cbf4a2ee?size=100&default=https://api.adorable.io/avatars/285/loic-knuchel.png"
        val res = Image.GravatarUrl(
          hash = "9a2ae971f81eaff1f0da3d56cbf4a2ee",
          params = Seq("size" -> "100", "default" -> "https://api.adorable.io/avatars/285/loic-knuchel.png"))
        Image.GravatarUrl.parse(url).get shouldBe res
        res.value shouldBe url
      }
    }
  }
}
