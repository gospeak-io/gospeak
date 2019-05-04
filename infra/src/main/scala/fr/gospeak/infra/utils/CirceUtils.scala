package fr.gospeak.infra.utils

import io.circe.{Decoder, Encoder}
import shapeless.{::, Generic, HNil, Lazy}

object CirceUtils {
  implicit def decodeValueClass[T <: AnyVal, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]], d: Decoder[V]): Decoder[T] =
    Decoder.instance { cursor =>
      d(cursor).map { value =>
        g.value.from(value :: HNil)
      }
    }

  implicit def encodeValueClass[T <: AnyVal, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]], e: Encoder[V]): Encoder[T] =
    Encoder.instance { value =>
      e(g.value.to(value).head)
    }

  implicit def decodeSingleValueClass[T, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]], d: Decoder[V]): Decoder[T] =
    Decoder.instance { cursor =>
      d(cursor).map { value =>
        g.value.from(value :: HNil)
      }
    }

  implicit def encodeSingleValueClass[T, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]], e: Encoder[V]): Encoder[T] =
    Encoder.instance { value =>
      e(g.value.to(value).head)
    }
}
