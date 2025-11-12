package io.github.mbannour.mongo.codecs

import scala.reflect.{ClassTag, Enum as ScalaEnum}
import scala.quoted.*

import org.bson.*
import org.bson.codecs.*
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import _root_.io.github.mbannour.bson.macros.EnumCodecGenerator

/** `EnumValueCodecProvider` is a helper object to generate a MongoDB [[org.bson.codecs.configuration.CodecProvider]] for Scala 3 enums that
  * can be uniquely represented by a single primitive value such as an `Int`, `String`, or `Boolean`.
  *
  * This allows seamless BSON serialization/deserialization of Scala enums by mapping them to their primitive representation when writing to
  * and reading from MongoDB.
  *
  * This implementation uses compile-time macros to avoid reflection, making it more robust and performant.
  */
object EnumValueCodecProvider:

  /** Creates a [[org.bson.codecs.configuration.CodecProvider]] for a Scala 3 enum type `E` that is represented as a string (using its
    * name).
    *
    * Example usage: EnumValueCodecProvider.forStringEnum[Priority]
    */
  inline def forStringEnum[E <: ScalaEnum: ClassTag]: CodecProvider =
    ${ forStringEnumImpl[E]('{ summon[ClassTag[E]] }) }

  /** Creates a [[org.bson.codecs.configuration.CodecProvider]] for a Scala 3 enum type `E` that is represented as its ordinal (using its
    * index).
    *
    * Example usage: EnumValueCodecProvider.forOrdinalEnum[Priority]
    */
  inline def forOrdinalEnum[E <: ScalaEnum: ClassTag]: CodecProvider =
    ${ forOrdinalEnumImpl[E]('{ summon[ClassTag[E]] }) }

  private def forStringEnumImpl[E <: ScalaEnum: Type](ct: Expr[ClassTag[E]])(using Quotes): Expr[CodecProvider] =
    '{
      given ctGiven: ClassTag[E] = $ct
      given Codec[String] = new org.bson.codecs.StringCodec()
      apply[E, String](
        enumValue => EnumCodecGenerator.toString[E](enumValue, ""),
        str => EnumCodecGenerator.fromString[E](str, "")
      )
    }

  private def forOrdinalEnumImpl[E <: ScalaEnum: Type](ct: Expr[ClassTag[E]])(using Quotes): Expr[CodecProvider] =
    '{
      given ctGiven: ClassTag[E] = $ct
      given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]
      apply[E, Int](
        enumValue => EnumCodecGenerator.toInt[E](enumValue, ""),
        ord => EnumCodecGenerator.fromInt[E](ord, "")
      )
    }

  /** Creates a [[org.bson.codecs.configuration.CodecProvider]] for a Scala 3 enum type `E` that can be encoded as a primitive type `V`
    * (e.g., `Int`, `String`).
    *
    * @param toValue
    *   A function to extract the primitive representation (`V`) from the enum instance (`E`)
    * @param fromValue
    *   A function to reconstruct the enum instance (`E`) from its primitive representation (`V`)
    * @param valueCodec
    *   An implicit MongoDB [[org.bson.codecs.Codec]] for the primitive type `V`
    * @tparam E
    *   The enum type. Must be a Scala 3 `enum` (i.e., subtype of `scala.reflect.Enum`)
    * @tparam V
    *   The primitive value type to use for MongoDB representation (e.g., `Int`, `String`)
    * @return
    *   A [[org.bson.codecs.configuration.CodecProvider]] that supplies BSON codecs for the enum type `E`
    */
  def apply[E <: ScalaEnum: ClassTag, V](
      toValue: E => V,
      fromValue: V => E
  )(using valueCodec: Codec[V]): CodecProvider =

    val enumClass = summon[ClassTag[E]].runtimeClass.asInstanceOf[Class[E]]

    new CodecProvider:

      override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
        if enumClass.isAssignableFrom(clazz) then

          val enumCodec = new Codec[E]:

            override def encode(w: BsonWriter, value: E, ec: EncoderContext): Unit =
              val prim: V = toValue(value)
              valueCodec.encode(w, prim, ec)

            override def decode(r: BsonReader, dc: DecoderContext): E =
              val prim: V = valueCodec.decode(r, dc)
              fromValue(prim)

            override def getEncoderClass: Class[E] = enumClass

          enumCodec.asInstanceOf[Codec[T]]
        else null
    end new
  end apply
end EnumValueCodecProvider
