package gospeak.infra.services.upload

import gospeak.core.services.cloudinary.CloudinarySrv
import gospeak.core.services.upload.UploadConf
import gospeak.infra.services.cloudinary.{CloudinaryFakeSrv, CloudinarySrvImpl}

object UploadSrvFactory {
  def from(conf: UploadConf): CloudinarySrv = conf match {
    case c: UploadConf.Cloudinary => CloudinarySrvImpl.from(c)
    case _ => new CloudinaryFakeSrv()
  }
}
