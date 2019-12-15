package fr.gospeak.infra.testingutils

import java.util.UUID

import fr.gospeak.core.GospeakConf
import fr.gospeak.core.domain.utils.TemplateData
import fr.gospeak.core.services.cloudinary.CloudinarySrv
import fr.gospeak.core.services.storage.DatabaseConf
import fr.gospeak.core.services.upload.UploadConf
import fr.gospeak.infra.libs.cloudinary.CloudinaryClient
import fr.gospeak.infra.services.cloudinary.CloudinarySrvImpl
import fr.gospeak.infra.services.storage.sql.GospeakDbSql
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl

object Values {
  private val dbConf = DatabaseConf.H2(s"jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
  private val gsConf = GospeakConf(
    event = GospeakConf.EventConf(
      description = MustacheMarkdownTmpl[TemplateData.EventInfo]("Default event description")))
  private val cloudinarySrv: CloudinarySrv = new CloudinarySrvImpl(new CloudinaryClient(UploadConf.Url()))

  val db = new GospeakDbSql(dbConf, gsConf, cloudinarySrv)
}
