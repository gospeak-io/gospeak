package fr.gospeak.infra.services.storage.sql.tables

import java.time.Instant

import doobie.implicits._
import doobie.util.fragment.Fragment
import fr.gospeak.core.domain.UserRequest.{AccountValidationRequest, PasswordResetRequest}
import fr.gospeak.core.domain.{User, UserRequest}
import fr.gospeak.infra.utils.DoobieUtils.Fragments._
import fr.gospeak.infra.utils.DoobieUtils.Mappings._
import fr.gospeak.libs.scalautils.domain.EmailAddress

object UserRequestTable {
  private val _ = userRequestIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = "requests"
  private val tableFr: Fragment = Fragment.const0(table)

  object AccountValidation {
    private val kind = "AccountValidation"
    private val fields = Seq("id", "kind", "email", "user_id", "deadline", "created", "accepted")

    def insert(elt: AccountValidationRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.user}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      buildInsert(tableFr, Fragment.const0(fields.mkString(", ")), values).update
    }

    def accept(id: UserRequest.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now", where(id, now)).update

    def selectPending(id: UserRequest.Id, now: Instant): doobie.Query0[AccountValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[AccountValidationRequest]

    def selectPending(id: User.Id, now: Instant): doobie.Query0[AccountValidationRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[AccountValidationRequest]

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"

    private def where(id: User.Id, now: Instant): Fragment = fr0"WHERE user_id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"
  }

  object ResetPassword {
    private val kind = "ResetPassword"
    private val fields = Seq("id", "kind", "email", "deadline", "created", "accepted")

    def insert(elt: PasswordResetRequest): doobie.Update0 = {
      val values = fr0"${elt.id}, $kind, ${elt.email}, ${elt.deadline}, ${elt.created}, ${elt.accepted}"
      buildInsert(tableFr, Fragment.const0(fields.mkString(", ")), values).update
    }

    def accept(id: UserRequest.Id, now: Instant): doobie.Update0 =
      buildUpdate(tableFr, fr0"accepted=$now", where(id, now)).update

    def selectPending(id: UserRequest.Id, now: Instant): doobie.Query0[PasswordResetRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(id, now)).query[PasswordResetRequest]

    def selectPending(email: EmailAddress, now: Instant): doobie.Query0[PasswordResetRequest] =
      buildSelect(tableFr, Fragment.const0(fields.filter(_ != "kind").mkString(", ")), where(email, now)).query[PasswordResetRequest]

    private def where(id: UserRequest.Id, now: Instant): Fragment = fr0"WHERE id=$id AND kind=$kind AND deadline > $now AND accepted IS NULL"

    private def where(email: EmailAddress, now: Instant): Fragment = fr0"WHERE email=$email AND kind=$kind AND deadline > $now AND accepted IS NULL"
  }

}
