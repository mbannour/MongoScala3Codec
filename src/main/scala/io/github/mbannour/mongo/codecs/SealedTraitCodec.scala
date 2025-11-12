package io.github.mbannour.mongo.codecs

import org.bson.{BsonReader, BsonWriter, BsonType}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecRegistry
import scala.collection.mutable

/** A codec for sealed trait types that uses discriminator-based encoding/decoding.
  *
  * This codec wraps sealed trait values in a document with a discriminator field that identifies the concrete type, enabling polymorphic
  * serialization.
  *
  * ===Example BSON Structure===
  * For a sealed trait `Status` with subtypes `Pending` and `Completed(amount: Double)`:
  * {{{
  * {
  *   "_type": "Completed",
  *   "amount": 100.0
  * }
  * }}}
  *
  * @param discriminatorField
  *   The BSON field name for the discriminator (default: "_type")
  * @param discriminatorToClass
  *   Maps discriminator values to runtime classes for decoding
  * @param classToDiscriminator
  *   Maps runtime classes to discriminator values for encoding
  * @param registry
  *   The codec registry for looking up codecs for concrete subtypes
  * @param encoderClass
  *   The runtime class of the sealed trait (for codec registration)
  * @tparam T
  *   The sealed trait type
  */
class SealedTraitCodec[T](
    discriminatorField: String,
    discriminatorToClass: Map[String, Class[?]],
    classToDiscriminator: Map[Class[?], String],
    registry: CodecRegistry,
    override val getEncoderClass: Class[T]
) extends Codec[T]:

  override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
    if value == null then
      throw new IllegalArgumentException("Cannot encode null sealed trait value")

    val actualClass = value.getClass
    val discriminator = classToDiscriminator.getOrElse(
      actualClass,
      throw new IllegalArgumentException(
        s"No discriminator mapping found for class ${actualClass.getName}. " +
          s"Available mappings: ${classToDiscriminator.keys.map(_.getName).mkString(", ")}"
      )
    )

    // Get the codec for the concrete type
    val concreteCodec = try
      registry.get(actualClass).asInstanceOf[Codec[T]]
    catch
      case e: Exception =>
        throw new IllegalArgumentException(
          s"No codec registered for concrete type ${actualClass.getName}. " +
            s"Ensure all sealed trait subtypes are registered in the registry.",
          e
        )

    // Write as a document with discriminator + fields
    writer.writeStartDocument()
    writer.writeString(discriminatorField, discriminator)

    // Encode the actual object's fields into a temporary document
    // We need to read what the concrete codec would write and merge it into our document
    val tempDocument = new org.bson.BsonDocument()
    val tempWriter = new org.bson.BsonDocumentWriter(tempDocument)
    concreteCodec.encode(tempWriter, value, encoderContext)

    // Copy all fields from the concrete codec's output, except the discriminator if it exists
    val iterator = tempDocument.entrySet().iterator()
    while iterator.hasNext do
      val entry = iterator.next()
      val key = entry.getKey
      if key != discriminatorField then writer.writeName(key)
        tempDocument.get(key).asInstanceOf[org.bson.BsonValue] match
          case v: org.bson.BsonString     => writer.writeString(v.getValue)
          case v: org.bson.BsonInt32      => writer.writeInt32(v.getValue)
          case v: org.bson.BsonInt64      => writer.writeInt64(v.getValue)
          case v: org.bson.BsonDouble     => writer.writeDouble(v.getValue)
          case v: org.bson.BsonBoolean    => writer.writeBoolean(v.getValue)
          case _: org.bson.BsonNull       => writer.writeNull()
          case v: org.bson.BsonDocument   => new org.bson.codecs.BsonDocumentCodec().encode(writer, v, encoderContext)
          case v: org.bson.BsonArray      => new org.bson.codecs.BsonArrayCodec().encode(writer, v, encoderContext)
          case v: org.bson.BsonObjectId   => writer.writeObjectId(v.getValue)
          case v: org.bson.BsonBinary     => writer.writeBinaryData(v)
          case v: org.bson.BsonTimestamp  => writer.writeTimestamp(v)
          case v: org.bson.BsonDateTime   => writer.writeDateTime(v.getValue)
          case v: org.bson.BsonDecimal128 => writer.writeDecimal128(v.getValue)
          case v: org.bson.BsonRegularExpression => writer.writeRegularExpression(v)
          case v: org.bson.BsonSymbol     => writer.writeSymbol(v.getSymbol)
          case _: org.bson.BsonUndefined  => writer.writeUndefined()
          case v: org.bson.BsonJavaScript => writer.writeJavaScript(v.getCode)
          case v: org.bson.BsonJavaScriptWithScope =>
            writer.writeJavaScriptWithScope(v.getCode)
            new org.bson.codecs.BsonDocumentCodec().encode(writer, v.getScope, encoderContext)
          case _: org.bson.BsonMinKey  => writer.writeMinKey()
          case _: org.bson.BsonMaxKey  => writer.writeMaxKey()
          case v: org.bson.BsonDbPointer => writer.writeDBPointer(v)
          case other =>
            throw new IllegalArgumentException(s"Unsupported BSON type: ${other.getClass.getName}")

    writer.writeEndDocument()
  end encode

  override def decode(reader: BsonReader, decoderContext: DecoderContext): T =
    reader.readStartDocument()

    // Read all fields into a map, looking for the discriminator
    val fieldsData = mutable.Map.empty[String, Any]
    var discriminator: Option[String] = None

    while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
      val fieldName = reader.readName()
      if fieldName == discriminatorField then
        discriminator = Some(reader.readString())
      else
        // Store field for later processing
        fieldsData += (fieldName -> readBsonValue(reader, decoderContext))
    end while

    reader.readEndDocument()

    // Determine the concrete class from the discriminator
    val concreteClass = discriminator match
      case Some(disc) =>
        discriminatorToClass.getOrElse(
          disc,
          throw new IllegalArgumentException(
            s"Unknown discriminator value '$disc' for sealed trait ${getEncoderClass.getName}. " +
              s"Valid discriminators: ${discriminatorToClass.keys.mkString(", ")}"
          )
        )
      case None =>
        throw new IllegalArgumentException(
          s"Missing discriminator field '$discriminatorField' when decoding sealed trait ${getEncoderClass.getName}"
        )

    // Get the codec for the concrete type
    val concreteCodec = try
      registry.get(concreteClass).asInstanceOf[Codec[T]]
    catch
      case e: Exception =>
        throw new IllegalArgumentException(
          s"No codec registered for concrete type ${concreteClass.getName}",
          e
        )

    // Reconstruct the document without the discriminator and decode
    val reconstructedDoc = new org.bson.BsonDocument()
    fieldsData.foreach { case (key, value) =>
      reconstructedDoc.put(key, toBsonValue(value))
    }

    val docReader = new org.bson.BsonDocumentReader(reconstructedDoc)
    concreteCodec.decode(docReader, decoderContext)
  end decode

  /** Reads a BSON value from the reader and returns it as a Scala type. */
  private def readBsonValue(reader: BsonReader, decoderContext: DecoderContext): Any =
    reader.getCurrentBsonType match
      case BsonType.STRING       => reader.readString()
      case BsonType.INT32        => reader.readInt32()
      case BsonType.INT64        => reader.readInt64()
      case BsonType.DOUBLE       => reader.readDouble()
      case BsonType.BOOLEAN      => reader.readBoolean()
      case BsonType.NULL         => reader.readNull(); null
      case BsonType.OBJECT_ID    => reader.readObjectId()
      case BsonType.DATE_TIME    => reader.readDateTime()
      case BsonType.BINARY       => reader.readBinaryData()
      case BsonType.TIMESTAMP    => reader.readTimestamp()
      case BsonType.DECIMAL128   => reader.readDecimal128()
      case BsonType.REGULAR_EXPRESSION => reader.readRegularExpression()
      case BsonType.SYMBOL       => reader.readSymbol()
      case BsonType.UNDEFINED    => reader.readUndefined(); ()
      case BsonType.JAVASCRIPT   => reader.readJavaScript()
      case BsonType.JAVASCRIPT_WITH_SCOPE =>
        val code = reader.readJavaScriptWithScope()
        val scope = new org.bson.codecs.BsonDocumentCodec().decode(reader, decoderContext)
        (code, scope)
      case BsonType.MIN_KEY => reader.readMinKey(); new org.bson.BsonMinKey()
      case BsonType.MAX_KEY => reader.readMaxKey(); new org.bson.BsonMaxKey()
      case BsonType.DB_POINTER => reader.readDBPointer()
      case BsonType.DOCUMENT =>
        reader.readStartDocument()
        val doc = mutable.Map.empty[String, Any]
        while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
          val key = reader.readName()
          doc += (key -> readBsonValue(reader, decoderContext))
        reader.readEndDocument()
        doc.toMap
      case BsonType.ARRAY =>
        reader.readStartArray()
        val arr = mutable.ListBuffer.empty[Any]
        while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
          arr += readBsonValue(reader, decoderContext)
        reader.readEndArray()
        arr.toList
      case other =>
        throw new IllegalArgumentException(s"Unsupported BSON type: $other")

  /** Converts a Scala value back to a BsonValue. */
  private def toBsonValue(value: Any): org.bson.BsonValue =
    value match
      case s: String   => new org.bson.BsonString(s)
      case i: Int      => new org.bson.BsonInt32(i)
      case l: Long     => new org.bson.BsonInt64(l)
      case d: Double   => new org.bson.BsonDouble(d)
      case b: Boolean  => new org.bson.BsonBoolean(b)
      case null        => new org.bson.BsonNull()
      case oid: org.bson.types.ObjectId => new org.bson.BsonObjectId(oid)
      case bin: org.bson.BsonBinary => bin
      case ts: org.bson.BsonTimestamp => ts
      case dec: org.bson.types.Decimal128 => new org.bson.BsonDecimal128(dec)
      case re: org.bson.BsonRegularExpression => re
      case map: Map[?, ?] =>
        val doc = new org.bson.BsonDocument()
        map.foreach { case (k, v) =>
          doc.put(k.toString, toBsonValue(v))
        }
        doc
      case list: List[?] =>
        val arr = new org.bson.BsonArray()
        list.foreach(item => arr.add(toBsonValue(item)))
        arr
      case other =>
        throw new IllegalArgumentException(s"Cannot convert value to BsonValue: ${other.getClass.getName}")

end SealedTraitCodec

object SealedTraitCodec:
  /** Creates a SealedTraitCodec for a sealed trait type using compile-time macro.
    *
    * @tparam T
    *   The sealed trait type
    * @param config
    *   The codec configuration containing discriminator settings
    * @param registry
    *   The codec registry for looking up concrete type codecs
    * @param classTag
    *   ClassTag for the sealed trait type
    * @return
    *   A codec for the sealed trait
    */
  inline def apply[T](
      config: CodecConfig,
      registry: CodecRegistry
  )(using classTag: scala.reflect.ClassTag[T]): SealedTraitCodec[T] =
    ${ applyImpl[T]('config, 'registry, 'classTag) }

  import scala.quoted.*

  private def applyImpl[T: Type](
      config: Expr[CodecConfig],
      registry: Expr[CodecRegistry],
      classTag: Expr[scala.reflect.ClassTag[T]]
  )(using Quotes): Expr[SealedTraitCodec[T]] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    // Verify this is a sealed trait
    if !sym.flags.is(Flags.Sealed) then
      report.errorAndAbort(s"Type ${sym.name} must be a sealed trait or sealed abstract class")

    '{
      val discriminatorField = $config.discriminatorField
      val discriminatorToClass = io.github.mbannour.bson.macros.SealedTraitHelper.createDiscriminatorMap[T](
        $config.discriminatorStrategy
      )
      val classToDiscriminator = io.github.mbannour.bson.macros.SealedTraitHelper.createReverseDiscriminatorMap[T](
        $config.discriminatorStrategy
      )
      val encoderClass = $classTag.runtimeClass.asInstanceOf[Class[T]]

      new SealedTraitCodec[T](
        discriminatorField,
        discriminatorToClass,
        classToDiscriminator,
        $registry,
        encoderClass
      )
    }
  end applyImpl
end SealedTraitCodec
