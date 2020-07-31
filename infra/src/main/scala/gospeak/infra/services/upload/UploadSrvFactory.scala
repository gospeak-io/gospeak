package gospeak.infra.services.upload

import gospeak.core.services.cloudinary.CloudinarySrv
import gospeak.core.services.upload.UploadConf
import gospeak.infra.services.cloudinary.{CloudinaryFakeSrv, CloudinarySrvImpl}
import gospeak.libs.http.HttpClient

object UploadSrvFactory {
  def from(conf: UploadConf, http: HttpClient): CloudinarySrv = conf match {
    case c: UploadConf.Cloudinary => CloudinarySrvImpl.from(c, http)
    case _ => new CloudinaryFakeSrv()
  }
}
