package gospeak.libs.sql.dsl

import java.sql.Types._

sealed abstract class JdbcType(val toInt: Int) extends Product with Serializable

object JdbcType {

  case object Array extends JdbcType(ARRAY)

  case object BigInt extends JdbcType(BIGINT)

  case object Binary extends JdbcType(BINARY)

  case object Bit extends JdbcType(BIT)

  case object Blob extends JdbcType(BLOB)

  case object Boolean extends JdbcType(BOOLEAN)

  case object Char extends JdbcType(CHAR)

  case object Clob extends JdbcType(CLOB)

  case object DataLink extends JdbcType(DATALINK)

  case object Date extends JdbcType(DATE)

  case object Decimal extends JdbcType(DECIMAL)

  case object Distinct extends JdbcType(DISTINCT)

  case object Double extends JdbcType(DOUBLE)

  case object Float extends JdbcType(FLOAT)

  case object Integer extends JdbcType(INTEGER)

  case object JavaObject extends JdbcType(JAVA_OBJECT)

  case object LongnVarChar extends JdbcType(LONGNVARCHAR)

  case object LongVarBinary extends JdbcType(LONGVARBINARY)

  case object LongVarChar extends JdbcType(LONGVARCHAR)

  case object NChar extends JdbcType(NCHAR)

  case object NClob extends JdbcType(NCLOB)

  case object Null extends JdbcType(NULL)

  case object Numeric extends JdbcType(NUMERIC)

  case object NVarChar extends JdbcType(NVARCHAR)

  case object Other extends JdbcType(OTHER)

  case object Real extends JdbcType(REAL)

  case object Ref extends JdbcType(REF)

  case object RefCursor extends JdbcType(REF_CURSOR)

  case object RowId extends JdbcType(ROWID)

  case object SmallInt extends JdbcType(SMALLINT)

  case object SqlXml extends JdbcType(SQLXML)

  case object Struct extends JdbcType(STRUCT)

  case object Time extends JdbcType(TIME)

  case object TimeWithTimezone extends JdbcType(TIME_WITH_TIMEZONE)

  case object Timestamp extends JdbcType(TIMESTAMP)

  case object TimestampWithTimezone extends JdbcType(TIMESTAMP_WITH_TIMEZONE)

  case object TinyInt extends JdbcType(TINYINT)

  case object VarBinary extends JdbcType(VARBINARY)

  case object VarChar extends JdbcType(VARCHAR)

  case object MsSqlDateTimeOffset extends JdbcType(-155)

  case object MsSqlVariant extends JdbcType(-150)

  final case class Unknown(override val toInt: Int) extends JdbcType(toInt)

  def fromInt(n: Int): JdbcType = n match {
    case Array.toInt => Array
    case BigInt.toInt => BigInt
    case Binary.toInt => Binary
    case Bit.toInt => Bit
    case Blob.toInt => Blob
    case Boolean.toInt => Boolean
    case Char.toInt => Char
    case Clob.toInt => Clob
    case DataLink.toInt => DataLink
    case Date.toInt => Date
    case Decimal.toInt => Decimal
    case Distinct.toInt => Distinct
    case Double.toInt => Double
    case Float.toInt => Float
    case Integer.toInt => Integer
    case JavaObject.toInt => JavaObject
    case LongnVarChar.toInt => LongnVarChar
    case LongVarBinary.toInt => LongVarBinary
    case LongVarChar.toInt => LongVarChar
    case NChar.toInt => NChar
    case NClob.toInt => NClob
    case Null.toInt => Null
    case Numeric.toInt => Numeric
    case NVarChar.toInt => NVarChar
    case Other.toInt => Other
    case Real.toInt => Real
    case Ref.toInt => Ref
    case RefCursor.toInt => RefCursor
    case RowId.toInt => RowId
    case SmallInt.toInt => SmallInt
    case SqlXml.toInt => SqlXml
    case Struct.toInt => Struct
    case Time.toInt => Time
    case TimeWithTimezone.toInt => TimeWithTimezone
    case Timestamp.toInt => Timestamp
    case TimestampWithTimezone.toInt => TimestampWithTimezone
    case TinyInt.toInt => TinyInt
    case VarBinary.toInt => VarBinary
    case VarChar.toInt => VarChar
    case MsSqlDateTimeOffset.toInt => MsSqlDateTimeOffset
    case MsSqlVariant.toInt => MsSqlVariant
    case -10 => NVarChar
    case n => Unknown(n)
  }
}
