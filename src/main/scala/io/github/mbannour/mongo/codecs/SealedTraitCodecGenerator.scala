package io.github.mbannour.mongo.codecs

import scala.jdk.CollectionConverters.*
import scala.quoted.*
import scala.reflect.ClassTag

import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonInvalidOperationException, BsonReader, BsonType, BsonWriter}

import io.github.mbannour.bson.macros.CaseClassMapper

/** Macro-based codec generator for sealed traits and sealed classes.
  *
  * Generates discriminator-based polymorphic codecs that store the concrete type in a discriminator field (default: "_type") and delegate
  * encoding/decoding to the appropriate concrete case class codec.
  *
  * Example:
  * {{{
  *   sealed trait Animal
  *   case class Dog(name: String, breed: String) extends Animal
  *   case class Cat(name: String, lives: Int) extends Animal
  *
  *   val registry = baseRegistry.newBuilder.registerSealed[Animal].build
  *   val codec = registry.get(classOf[Animal])
  *
  *   // Encodes to: {"_type": "Dog", "name": "Rex", "breed": "Labrador"}
  * }}}
  */
object SealedTraitCodecGenerator:

  /** Generates a BSON codec for sealed trait/class `T`.
    *
    * @param config
    *   Configuration for codec behavior (discriminator field name, None handling, etc.)
    * @param codecRegistry
    *   The base CodecRegistry used for looking up concrete subclass codecs
    * @tparam T
    *   The sealed trait/class type
    * @return
    *   A BSON Codec[T] that handles all concrete case class subtypes
    */
  private[codecs] inline def generateSealedCodec[T](config: CodecConfig, codecRegistry: CodecRegistry)(using
      classTag: ClassTag[T]
  ): Codec[T] =
    ${ generateSealedCodecImpl[T]('config, 'codecRegistry, 'classTag) }

  /** Macro implementation for generating a sealed trait/class codec.
    *
    * This generates a codec that:
    *   1. During encoding:
    *      - Writes discriminator field with the concrete class name
    *      - Delegates field writing to the concrete case class codec 2. During decoding:
    *      - Uses mark/reset to peek at the discriminator field
    *      - Looks up the appropriate concrete codec
    *      - Delegates to that codec for full decoding
    *
    * @param config
    *   The codec configuration
    * @param baseRegistry
    *   The registry for looking up subclass codecs
    * @param classTag
    *   The ClassTag for type T
    * @return
    *   An Expr representing a Codec[T]
    */
  private[codecs] def generateSealedCodecImpl[T: Type](
      config: Expr[CodecConfig],
      baseRegistry: Expr[CodecRegistry],
      classTag: Expr[ClassTag[T]]
  )(using Quotes): Expr[Codec[T]] =
    import quotes.reflect.*

    val tpeSym = TypeRepr.of[T].typeSymbol
    val typeName = tpeSym.name

    // Validate that T is sealed
    if !tpeSym.flags.is(Flags.Sealed) then
      report.errorAndAbort(
        s"Cannot generate sealed trait codec for '$typeName' - it is not a sealed type.\n" +
          "Only sealed traits, sealed classes, and sealed abstract classes are supported."
      )

    val caseClassesMapExpr = '{ CaseClassMapper.caseClassMap[T] }

    '{
      new Codec[T]:
        private val encoderClass: Class[T] = $classTag.runtimeClass.asInstanceOf[Class[T]]
        private val codecConfig: CodecConfig = $config

        private val caseClassesMap: Map[String, Class[?]] = $caseClassesMapExpr

        private lazy val caseClassesMapInv: Map[Class[?], String] = caseClassesMap.map(_.swap)

        if caseClassesMap.isEmpty then
          throw new IllegalStateException(
            s"No case class subclasses found for sealed type ${encoderClass.getName}. " +
              "Sealed traits must have at least one case class subtype."
          )

        private lazy val registry: CodecRegistry = $baseRegistry

        override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
          if value == null then
            throw new BsonInvalidOperationException(
              s"Cannot encode null value for sealed trait ${encoderClass.getName}. " +
                "BSON codecs do not support null root values."
            )

          val runtimeClass = value.getClass
          val discriminator = caseClassesMapInv.getOrElse(
            runtimeClass,
            throw new IllegalArgumentException(
              s"Unknown subclass: ${runtimeClass.getName} is not a registered subtype of ${encoderClass.getName}. " +
                s"Known subtypes: ${caseClassesMapInv.keys.map(_.getName).mkString(", ")}"
            )
          )

          val concreteCodec = registry.get(runtimeClass.asInstanceOf[Class[Any]])

          // Start the document and write the discriminator
          writer.writeStartDocument()
          writer.writeString(codecConfig.discriminatorField, discriminator)

          // Get the concrete codec to encode the value
          // We need to manually write each field from the concrete case class
          // We'll use a temporary document to encode the concrete type, then copy fields
          val tempDoc = new org.bson.BsonDocument()
          val tempWriter = new org.bson.BsonDocumentWriter(tempDoc)

          concreteCodec.encode(tempWriter, value, encoderContext)

          val bsonValueCodec = new org.bson.codecs.BsonValueCodec()
          for entry <- tempDoc.entrySet().asScala do
            if entry.getKey != codecConfig.discriminatorField then
              writer.writeName(entry.getKey)
              bsonValueCodec.encode(writer, entry.getValue, encoderContext)

          writer.writeEndDocument()
        end encode

        override def decode(reader: BsonReader, decoderContext: DecoderContext): T =

          val mark = reader.getMark()
          reader.readStartDocument()

          // Scan through fields to find the discriminator
          var discriminator: Option[String] = None
          while reader.readBsonType() != BsonType.END_OF_DOCUMENT && discriminator.isEmpty do
            val fieldName = reader.readName()
            if fieldName == codecConfig.discriminatorField then discriminator = Some(reader.readString())
            else reader.skipValue()
          end while
          // Reset reader to beginning of document
          mark.reset()

          // Validate discriminator was found
          val className = discriminator.getOrElse(
            throw new BsonInvalidOperationException(
              s"Could not decode sealed trait ${encoderClass.getName}. " +
                s"Missing discriminator field '${codecConfig.discriminatorField}' in BSON document. " +
                s"Make sure the document was encoded with a discriminator field."
            )
          )

          // Look up the concrete class
          val concreteClass = caseClassesMap.getOrElse(
            className,
            throw new BsonInvalidOperationException(
              s"Could not decode sealed trait ${encoderClass.getName}. " +
                s"Unknown discriminator value '$className'. " +
                s"Known discriminators: ${caseClassesMap.keys.mkString(", ")}"
            )
          )

          // Get codec for the concrete class and delegate decoding
          // The concrete codec will read the entire document including the discriminator field
          val concreteCodec = registry.get(concreteClass.asInstanceOf[Class[T]])
          concreteCodec.decode(reader, decoderContext)
        end decode

        override def getEncoderClass: Class[T] = encoderClass
    }
  end generateSealedCodecImpl
end SealedTraitCodecGenerator
