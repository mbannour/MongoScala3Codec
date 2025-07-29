package io.github.mbannour.mongo.codecs

import org.bson.*
import org.bson.codecs.*
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import scala.reflect.Enum as ScalaEnum
import scala.reflect.ClassTag

/** `EnumValueCodecProvider` is a helper object to generate a MongoDB [[org.bson.codecs.configuration.CodecProvider]] for Scala 3 enums that
  * can be uniquely represented by a single primitive value such as an `Int`, `String`, or `Boolean`.
  *
  * This allows seamless BSON serialization/deserialization of Scala enums by mapping them to their primitive representation when writing to
  * and reading from MongoDB.
  */
object EnumValueCodecProvider:

  /** Creates a [[CodecProvider]] for a Scala 3 enum type `E` that is represented as a string (using its name).
    *
    * Example usage: EnumValueCodecProvider.forStringEnum[Priority]
    */
  def forStringEnum[E <: ScalaEnum: ClassTag]: CodecProvider =
    given Codec[String] = new org.bson.codecs.StringCodec()
    apply[E, String](
      _.toString,
      str =>
        val enumClass = summon[ClassTag[E]].runtimeClass.asInstanceOf[Class[E]]
        val method = enumClass.getMethod("valueOf", classOf[String])
        method.invoke(enumClass, str).asInstanceOf[E]
    )
  end forStringEnum

  /** Creates a [[CodecProvider]] for a Scala 3 enum type `E` that is represented as its ordinal (using its index).
    *
    * Example usage: EnumValueCodecProvider.forOrdinalEnum[Priority]
    */
  def forOrdinalEnum[E <: ScalaEnum: ClassTag]: CodecProvider =
    given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]
    apply[E, Int](
      _.ordinal,
      ord =>
        val enumClass = summon[ClassTag[E]].runtimeClass.asInstanceOf[Class[E]]
        val values = enumClass.getMethod("values").invoke(enumClass).asInstanceOf[Array[Object]]
        if ord < 0 || ord >= values.length then throw new RuntimeException(s"No enum value with ordinal: $ord")
        values(ord).asInstanceOf[E]
    )
  end forOrdinalEnum

  /** Creates a [[CodecProvider]] for a Scala 3 enum type `E` that can be encoded as a primitive type `V` (e.g., `Int`, `String`).
    *
    * @param toValue
    *   A function to extract the primitive representation (`V`) from the enum instance (`E`)
    * @param fromValue
    *   A function to reconstruct the enum instance (`E`) from its primitive representation (`V`)
    * @param valueCodec
    *   An implicit MongoDB [[Codec]] for the primitive type `V`
    * @tparam E
    *   The enum type. Must be a Scala 3 `enum` (i.e., subtype of `scala.reflect.Enum`)
    * @tparam V
    *   The primitive value type to use for MongoDB representation (e.g., `Int`, `String`)
    * @return
    *   A [[CodecProvider]] that supplies BSON codecs for the enum type `E`
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
