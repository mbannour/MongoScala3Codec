package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import scala.reflect.ClassTag

/** Codec generator for BSON serialization and deserialization of case classes. */
object CaseClassCodec:

  /** Generates a BSON codec for a case class, including `None` values during serialization.
    *
    * @param classTag
    *   Implicit `ClassTag` for the case class type.
    * @tparam T
    *   The case class type for which the codec is generated.
    * @return
    *   A BSON codec instance for type `T`.
    */
  inline def generateCodecEncodeNone[T](using classTag: ClassTag[T]): Codec[T] =
    CaseClassCodecGenerator.generateCodec[T](encodeNone = true)

  /** Generates a BSON codec for a case class, excluding `None` values during serialization.
    *
    * @param classTag
    *   Implicit `ClassTag` for the case class type.
    * @tparam T
    *   The case class type for which the codec is generated.
    * @return
    *   A BSON codec instance for type `T`.
    */
  inline def generateCodecIgnoreNone[T](using classTag: ClassTag[T]): Codec[T] =
    CaseClassCodecGenerator.generateCodec[T](encodeNone = false)
end CaseClassCodec
