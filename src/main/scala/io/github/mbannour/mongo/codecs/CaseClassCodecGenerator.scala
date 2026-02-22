package io.github.mbannour.mongo.codecs

import java.util.UUID

import scala.collection.mutable
import scala.quoted.*
import scala.reflect.ClassTag

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, Encoder, EncoderContext}
import org.bson.{BsonInvalidOperationException, BsonReader, BsonType, BsonWriter}

import io.github.mbannour.bson.macros.*

/** Macro-based codec generator for BSON serialization/deserialization of case classes, supporting nested and sealed hierarchies.
  */
object CaseClassCodecGenerator:

  /** Generates a BSON codec for the given type `T`.
    *
    * @param config
    *   Configuration object for codec generation behavior.
    * @param codecRegistry
    *   The base CodecRegistry to be used for nested codec lookups.
    * @tparam T
    *   The case class type for which the codec is generated.
    * @return
    *   A BSON Codec[T] instance.
    */
  private[codecs] inline def generateCodec[T](config: CodecConfig, codecRegistry: CodecRegistry)(using
      classTag: ClassTag[T]
  ): Codec[T] =
    ${ generateCodecImpl[T]('config, 'codecRegistry, 'classTag) }

  /** Macro implementation for generating a BSON Codec for a case class or sealed hierarchy.
    *
    * This macro inspects the type T at compile time, verifies it is a case class, and generates serialization/deserialization logic for all
    * fields, including support for nested case classes and sealed traits.
    *
    * Improvements in this version:
    *   - Uses CodecConfig instead of boolean flags for better extensibility
    *   - Enhanced error messages with compile-time validation
    *   - Better handling of discriminator fields
    *   - Improved type safety
    *
    * @param config
    *   The codec configuration object.
    * @param baseRegistry
    *   The CodecRegistry used for nested lookups.
    * @param classTag
    *   The ClassTag for type T.
    * @return
    *   An Expr representing a Codec[T] instance.
    */
  private def generateCodecImpl[T: Type](
      config: Expr[CodecConfig],
      baseRegistry: Expr[CodecRegistry],
      classTag: Expr[ClassTag[T]]
  )(using Quotes): Expr[Codec[T]] =
    import quotes.reflect.*

    // Ensure T is a case class OR redirect sealed traits to SealedTraitCodecGenerator
    val tpeSym = TypeRepr.of[T].typeSymbol
    if !tpeSym.flags.is(Flags.Case) then
      val typeName = tpeSym.name
      val isSealed = tpeSym.flags.is(Flags.Sealed)

      // If it's a sealed type, redirect to SealedTraitCodecGenerator
      if isSealed then return SealedTraitCodecGenerator.generateSealedCodecImpl[T](config, baseRegistry, classTag)

      // Otherwise, generate error for non-case, non-sealed types
      val typeKind =
        if tpeSym.flags.is(Flags.Trait) then "trait"
        else if tpeSym.flags.is(Flags.Abstract) then "abstract class"
        else if tpeSym.isClassDef then "regular class"
        else "type"

      val errorMessage =
        s"Cannot generate BSON codec for '$typeName'" +
          s"\n\n'$typeName' is a $typeKind, but BSON codecs can only be generated for case classes or sealed types." +
          "\n\n" +
          "Suggestions:" +
          s"\n  • Convert '$typeName' to a case class: case class $typeName(...)" +
          "\n  • For sealed traits/classes, use registerSealed[$typeName] instead of register[$typeName]" +
          "\n  • For regular classes, create a case class wrapper" +
          "\n  • For abstract classes, use sealed trait + case class subtypes instead"

      report.errorAndAbort(errorMessage)
    end if

    '{
      new Codec[T]:
        /** The runtime class for type T, used for reflection and type checks.
          */
        private val encoderClass: Class[T] = $classTag.runtimeClass.asInstanceOf[Class[T]]

        /** Configuration for codec behavior (None handling, etc.)
          */
        private val codecConfig: CodecConfig = $config

        // Maps from discriminator values to classes and vice versa.
        private val caseClassesMap: Map[String, Class[?]] = CaseClassMapper.caseClassMap[T]
        private val classToCaseClassMap: Map[Class[?], Boolean] = ClassToCaseFlagMap.classToCaseClassMap[T]
        private val fieldTypeArgsMapByClass: Map[String, Map[String, List[Class[?]]]] = CaseClassFieldMapper.createClassFieldTypeArgsMap[T]
        private lazy val caseClassesMapInv: Map[Class[?], String] = caseClassesMap.map(_.swap)

        // Compose the base registry using the provided baseRegistry and the codec for this type.
        private val baseRegistryWithThis: CodecRegistry = CodecRegistries.fromRegistries(
          $baseRegistry,
          CodecRegistries.fromCodecs(this)
        )

        /** Lazy on-demand codec cache using CachedCodecRegistry.
          *
          * Performance improvement: Instead of eagerly pre-fetching all codecs at initialization (which can cause circular dependencies and
          * slow startup), we use a CachedCodecRegistry that fetches codecs on-demand as they're needed. The CachedCodecRegistry uses a
          * thread-safe ConcurrentHashMap for lock-free caching.
          *
          * Benefits:
          *   - Avoids circular dependency issues during initialization
          *   - Faster codec creation (no upfront codec fetching)
          *   - Thread-safe for concurrent encoding/decoding
          *   - Memory efficient (only caches codecs that are actually used)
          */
        private lazy val registry: CodecRegistry = new CachedCodecRegistry(baseRegistryWithThis)

        /** Gets a codec from the cached registry.
          * @param clazz
          *   The class to get a codec for
          * @return
          *   The codec for the given class
          */
        private def getCodec[V](clazz: Class[V]): Codec[V] = registry.get(clazz)

        private def getInstance(fieldsData: Map[String, Any]): T =
          CaseClassFactory.getInstance[T](fieldsData)

        override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
          if value == null then
            throw new BsonInvalidOperationException(
              s"Invalid value for $encoderClass: found a null value. BSON codecs do not support null root values."
            )
          else writeValue(writer, value, encoderContext)

        /** Writes a value of type V to the BSON writer. */
        private def writeValue[V](writer: BsonWriter, value: V, encoderContext: EncoderContext): Unit =
          writer.writeStartDocument()
          val clazz = value.getClass
          caseClassesMapInv.get(clazz) match
            case Some(discriminator) =>
              // When this codec is registered as a sealed subtype, write the discriminator so
              // that Updates.set and similar operations produce a self-describing BSON document
              // that the sealed-trait codec can decode later.
              if codecConfig.writeDiscriminator then writer.writeString(codecConfig.discriminatorField, discriminator)
              // For case classes, delegate to the helper writer with the discriminator.
              CaseClassBsonWriter.writeCaseClassData(
                discriminator,
                writer,
                value.asInstanceOf[T],
                encoderContext,
                codecConfig.shouldEncodeNone,
                registry
              )
            case None =>
              // Fallback: use the codec from the cache (or registry if not cached).
              val codec = getCodec(clazz).asInstanceOf[Encoder[V]]
              encoderContext.encodeWithChildContext(codec, writer, value)
          end match
          writer.writeEndDocument()
        end writeValue

        override def decode(reader: BsonReader, decoderContext: DecoderContext): T =
          val discriminator = caseClassesMap.head._1
          val fieldTypeArgs: Map[String, List[Class[?]]] = fieldTypeArgsMapByClass.getOrElse(discriminator, Map.empty)
          val fieldsData = mutable.Map.empty[String, Any]
          reader.readStartDocument()
          while reader.readBsonType != BsonType.END_OF_DOCUMENT do
            val name = reader.readName

            // Skip discriminator field if present (for sealed trait hierarchies)
            // The discriminator is used by SealedTraitCodecGenerator to determine the concrete type
            if name == codecConfig.discriminatorField then reader.skipValue()
            else
              val typeArgs = fieldTypeArgs.getOrElse(name, List.empty)
              if typeArgs.isEmpty then reader.skipValue()
              else
                val value = readValue(reader, decoderContext, typeArgs.head, typeArgs.tail, fieldTypeArgs)
                fieldsData += (name -> value)
          end while
          reader.readEndDocument()
          getInstance(fieldsData.toMap)
        end decode

        // Mapping from primitive types to their boxed counterparts.
        private val primitiveToBoxed: Map[Class[?], Class[?]] = Map(
          classOf[scala.Int] -> classOf[java.lang.Integer],
          classOf[scala.Long] -> classOf[java.lang.Long],
          classOf[scala.Double] -> classOf[java.lang.Double],
          classOf[scala.Float] -> classOf[java.lang.Float],
          classOf[scala.Boolean] -> classOf[java.lang.Boolean],
          classOf[scala.Short] -> classOf[java.lang.Short],
          classOf[scala.Byte] -> classOf[java.lang.Byte],
          classOf[scala.Char] -> classOf[java.lang.Character]
        )

        /** Specialized primitive readers to avoid boxing overhead.
          *
          * These methods read primitives directly from BSON without going through the codec lookup mechanism, avoiding unnecessary
          * boxing/unboxing and improving performance for primitive-heavy data structures.
          */
        @inline private def readInt(reader: BsonReader): Int = reader.readInt32()
        @inline private def readLong(reader: BsonReader): Long = reader.readInt64()
        @inline private def readDouble(reader: BsonReader): Double = reader.readDouble()
        @inline private def readBoolean(reader: BsonReader): Boolean = reader.readBoolean()
        @inline private def readString(reader: BsonReader): String = reader.readString()

        @inline private def readByte(reader: BsonReader): Byte = reader.readInt32().toByte
        @inline private def readShort(reader: BsonReader): Short = reader.readInt32().toShort
        @inline private def readChar(reader: BsonReader): Char = reader.readInt32().toChar

        @inline private def readFloat(reader: BsonReader): Float =
          // MongoDB stores Float as Double
          val doubleValue = reader.readDouble()
          if doubleValue.isNaN || doubleValue.isInfinite then doubleValue.toFloat
          else if doubleValue > Float.MaxValue || doubleValue < Float.MinValue then
            throw new BsonInvalidOperationException(
              s"Double value $doubleValue exceeds Float range (${Float.MinValue} to ${Float.MaxValue})"
            )
          else doubleValue.toFloat
        end readFloat

        /** Reads a value of type V from the BSON reader based on the current BSON type.
          *
          * Optimized with specialized primitive fast paths to avoid boxing overhead and unnecessary codec lookups for common primitive
          * types.
          */
        private def readValue[V](
            reader: BsonReader,
            decoderContext: DecoderContext,
            clazz: Class[V],
            typeArgs: List[Class[?]],
            fieldTypeArgs: Map[String, List[Class[?]]]
        ): V =
          reader.getCurrentBsonType match
            case BsonType.DOCUMENT =>
              readDocument(reader, decoderContext, clazz, typeArgs, fieldTypeArgs)
            case BsonType.ARRAY =>
              readArray(reader, decoderContext, clazz, typeArgs, fieldTypeArgs)
            case BsonType.NULL =>
              reader.readNull()
              null.asInstanceOf[V]

            // Specialized fast paths for primitives (avoid boxing and codec lookup)
            case BsonType.INT32 if clazz == classOf[Int] || clazz == classOf[java.lang.Integer] =>
              readInt(reader).asInstanceOf[V]
            case BsonType.INT64 if clazz == classOf[Long] || clazz == classOf[java.lang.Long] =>
              readLong(reader).asInstanceOf[V]
            case BsonType.DOUBLE if clazz == classOf[Double] || clazz == classOf[java.lang.Double] =>
              readDouble(reader).asInstanceOf[V]
            case BsonType.BOOLEAN if clazz == classOf[Boolean] || clazz == classOf[java.lang.Boolean] =>
              readBoolean(reader).asInstanceOf[V]
            case BsonType.STRING if clazz == classOf[String] =>
              readString(reader).asInstanceOf[V]
            case BsonType.INT32 if clazz == classOf[Byte] || clazz == classOf[java.lang.Byte] =>
              readByte(reader).asInstanceOf[V]
            case BsonType.INT32 if clazz == classOf[Short] || clazz == classOf[java.lang.Short] =>
              readShort(reader).asInstanceOf[V]
            case BsonType.INT32 if clazz == classOf[Char] || clazz == classOf[java.lang.Character] =>
              readChar(reader).asInstanceOf[V]
            case BsonType.DOUBLE if clazz == classOf[Float] || clazz == classOf[java.lang.Float] =>
              readFloat(reader).asInstanceOf[V]

            // UUID handling
            case BsonType.STRING if clazz == classOf[UUID] =>
              val stringValue = readString(reader)
              try UUID.fromString(stringValue).asInstanceOf[V]
              catch
                case _: IllegalArgumentException =>
                  throw new IllegalArgumentException(s"Invalid UUID string format: $stringValue")

            // Fallback to codec lookup for complex types
            case _ =>
              val effectiveClass = primitiveToBoxed.getOrElse(clazz, clazz).asInstanceOf[Class[V]]
              val codec = getCodec(effectiveClass)
              codec.decode(reader, decoderContext)

        /** Reads an array from the BSON reader and converts it to the proper collection type. */
        private def readArray[V](
            reader: BsonReader,
            decoderContext: DecoderContext,
            clazz: Class[V],
            typeArgs: List[Class[?]],
            fieldTypeArgs: Map[String, List[Class[?]]]
        ): V =
          if typeArgs.isEmpty then
            throw new BsonInvalidOperationException(
              s"Invalid BSON format for '${clazz.getSimpleName}'. Found an array but no type information available."
            )
          reader.readStartArray()
          val elements = mutable.ListBuffer.empty[Any]
          while reader.readBsonType != BsonType.END_OF_DOCUMENT do
            elements.append(readValue(reader, decoderContext, typeArgs.head, typeArgs.tail, fieldTypeArgs))
          reader.readEndArray()
          val result =
            if classOf[Set[?]].isAssignableFrom(clazz) then elements.toSet
            else if classOf[Vector[?]].isAssignableFrom(clazz) then elements.toVector
            else elements.toList
          result.asInstanceOf[V]
        end readArray

        /** Reads a document from the BSON reader and returns either a decoded object or a Map. */
        private def readDocument[V](
            reader: BsonReader,
            decoderContext: DecoderContext,
            clazz: Class[V],
            typeArgs: List[Class[?]],
            fieldTypeArgs: Map[String, List[Class[?]]]
        ): V =
          if classToCaseClassMap.getOrElse(clazz, false) || typeArgs.isEmpty then getCodec(clazz).decode(reader, decoderContext)
          else
            val docFields = mutable.Map.empty[String, Any]
            reader.readStartDocument()
            while reader.readBsonType != BsonType.END_OF_DOCUMENT do
              val name = reader.readName
              val fieldTypeArgsForField = fieldTypeArgs.getOrElse(name, typeArgs)
              if fieldTypeArgsForField.isEmpty then reader.skipValue()
              else
                docFields += (name -> readValue(
                  reader,
                  decoderContext,
                  fieldTypeArgsForField.head,
                  fieldTypeArgsForField.tail,
                  fieldTypeArgs
                ))
              end if
            end while
            reader.readEndDocument()
            docFields.toMap.asInstanceOf[V]

        override def getEncoderClass: Class[T] = encoderClass
    }
  end generateCodecImpl
end CaseClassCodecGenerator
