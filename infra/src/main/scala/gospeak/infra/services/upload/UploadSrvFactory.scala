package gospeak.infra.services.upload

import gospeak.core.services.cloudinary.CloudinarySrv
import gospeak.core.services.upload.UploadConf
import gospeak.infra.services.cloudinary.CloudinarySrvImpl

object UploadSrvFactory {
  def from(conf: UploadConf): Option[CloudinarySrv] = conf match {
    case c: UploadConf.Cloudinary => Some(CloudinarySrvImpl.from(c))
    case _ => None
  }
}
