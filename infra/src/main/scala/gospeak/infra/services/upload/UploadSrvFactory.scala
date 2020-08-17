package gospeak.infra.services.upload

import gospeak.core.services.cloudinary.UploadSrv
import gospeak.core.services.upload.UploadConf
import gospeak.libs.http.HttpClient

object UploadSrvFactory {
  def from(conf: UploadConf, http: HttpClient): UploadSrv = conf match {
    case c: UploadConf.Cloudinary => CloudinaryUploadSrv.from(c, http)
    case _: UploadConf.Url => new UrlUploadSrv()
  }
}
