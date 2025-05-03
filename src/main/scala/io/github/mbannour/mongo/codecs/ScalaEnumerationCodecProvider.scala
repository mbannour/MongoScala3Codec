package io.github.mbannour.mongo.codecs

import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

/** Provides a BSON CodecProvider for any Scala Enumeration.
  *
  * This allows MongoDB drivers to automatically serialize and deserialize `Enumeration.Value` instances when reading/writing documents,
  * using their string names.
  *
  * Example use:
  * {{{
  *   val priorityEnum = Priority  // your Enumeration
  *   val codecProvider = ScalaEnumerationCodecProvider(priorityEnum)
  *   val registry = CodecRegistries.fromProviders(codecProvider)
  *   val collection = mongoDatabase.withCodecRegistry(registry).getCollection("tasks")
  * }}}
  *
  * Serialization behavior:
  *   - Encodes an `Enumeration.Value` as a BSON string (using `.toString`).
  *   - Decodes a BSON string back to the corresponding `Enumeration.Value` via `withName`.
  *
  * Note:
  *   - The provider returns the codec only if the class matches `scala.Enumeration$Value` or a subclass.
  *   - You must register one provider per Enumeration instance.
  *
  * @author
  *   Mohamed Ali Bannour
  */
object ScalaEnumerationCodecProvider:

  /** Build a provider for one Enumeration instance (e.g. Priority) */
  def apply(enumeration: Enumeration): CodecProvider =

    val baseClass: Class[enumeration.Value] =
      classOf[Enumeration#Value].asInstanceOf[Class[enumeration.Value]]

    val codec: Codec[enumeration.Value] = new Codec[enumeration.Value]:
      override def encode(w: BsonWriter, v: enumeration.Value, c: EncoderContext): Unit =
        w.writeString(v.toString)

      override def decode(r: BsonReader, c: DecoderContext): enumeration.Value =
        enumeration.withName(r.readString())

      override def getEncoderClass: Class[enumeration.Value] = baseClass

    new CodecProvider:
      override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
        if baseClass.isAssignableFrom(clazz) then codec.asInstanceOf[Codec[T]]
        else null
  end apply
end ScalaEnumerationCodecProvider
