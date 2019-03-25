package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.UserRequest
import fr.gospeak.core.domain.UserRequest.EmailValidationRequest
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._

object UserRequestTable {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "requests"
  private val tableFr: Fragment = Fragment.const0(table)

  object EmailValidation {
    private val kind = "EmailValidation"
    private val fields = Seq("id", "kind", "email", "user_id", "deadline", "created", "accepted")

    def insert(elt: EmailValidationRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.user}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      buildInsert(tableFr, Fragment.const0(fields.mkString(", ")), values).update
    }

    def validateEmail(id: UserRequest.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now", where(id, now)).update

    def selectPendingEmailValidation(id: UserRequest.Id, now: Instant): doobie.Query0[EmailValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[EmailValidationRequest]

    private def where(id: UserRequest.Id, now: Instant): Fragment =
      fr0"WHERE id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"
  }

}
