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

    // Ensure T is a case class (or a sealed hierarchy of case classes)
    val tpeSym = TypeRepr.of[T].typeSymbol
    if !tpeSym.flags.is(Flags.Case) then
      val typeName = tpeSym.name
      val typeKind = if tpeSym.flags.is(Flags.Trait) then "trait"
                     else if tpeSym.flags.is(Flags.Abstract) then "abstract class"
                     else if tpeSym.isClassDef then "regular class"
                     else "type"
      report.errorAndAbort(
        s"Cannot generate BSON codec for '$typeName'" +
        s"\n\n'$typeName' is a $typeKind, but BSON codecs can only be generated for case classes." +
        "\n\nSuggestion:" +
        s"\n  • Convert '$typeName' to a case class: case class $typeName(...)" +
        "\n  • If this is a sealed trait, register each concrete case class implementation separately" +
        "\n  • For regular classes, consider creating a case class wrapper"
      )

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

        /** Pre-computed codec cache for frequently used types.
          * This cache is populated lazily to avoid circular initialization issues.
          * This significantly improves performance for nested case classes and collections.
          */
        private lazy val codecCache: Map[Class[?], Codec[?]] = {
          val cache = mutable.Map.empty[Class[?], Codec[?]]

          // Pre-fetch codecs for all field types (but not the current type to avoid circular dependency)
          fieldTypeArgsMapByClass.values.foreach { fieldTypeArgs =>
            fieldTypeArgs.values.foreach { typeArgsList =>
              typeArgsList.foreach { typeArg =>
                // Skip the current type to avoid circular initialization
                if (typeArg != encoderClass) {
                  try {
                    if (!cache.contains(typeArg)) {
                      cache.put(typeArg, baseRegistryWithThis.get(typeArg))
                    }
                  } catch {
                    case _: Exception => // Ignore types that don't have codecs yet
                  }
                }
              }
            }
          }

          // Pre-fetch codecs for all known case classes in the hierarchy (except current type)
          caseClassesMap.values.foreach { clazz =>
            if (clazz != encoderClass) {
              try {
                if (!cache.contains(clazz)) {
                  cache.put(clazz, baseRegistryWithThis.get(clazz))
                }
              } catch {
                case _: Exception => // Ignore classes that don't have codecs yet
              }
            }
          }

          cache.toMap
        }

        /** Cached registry that wraps the base registry with pre-fetched codecs.
          * This registry is used for all codec lookups to avoid repeated registry.get calls.
          * Marked as lazy to ensure codecCache is initialized first.
          */
        private lazy val registry: CodecRegistry = new CachedCodecRegistry(baseRegistryWithThis, codecCache)

        /** Gets a codec from the cached registry.
          * @param clazz The class to get a codec for
          * @return The codec for the given class
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

        /** Reads a value of type V from the BSON reader based on the current BSON type. */
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
              // Return null as-is, don't convert to primitive default values
              // This allows CaseClassFactory to properly handle Option[T] fields that contain null
              null.asInstanceOf[V]
            case BsonType.STRING if clazz == classOf[UUID] =>
              val stringValue = reader.readString()
              try UUID.fromString(stringValue).asInstanceOf[V]
              catch
                case _: IllegalArgumentException =>
                  throw new IllegalArgumentException(s"Invalid UUID string format: $stringValue")
            case BsonType.DOUBLE if clazz == classOf[Float] =>
              // Note: MongoDB stores Float as BSON Double (64-bit)
              // Converting back to Float may result in precision loss or overflow
              val doubleValue = reader.readDouble()
              if doubleValue.isNaN || doubleValue.isInfinite then doubleValue.toFloat.asInstanceOf[V]
              else if doubleValue > Float.MaxValue then
                throw new BsonInvalidOperationException(
                  s"Double value $doubleValue exceeds Float.MaxValue (${Float.MaxValue}). " +
                    s"Cannot safely convert to Float without overflow."
                )
              else if doubleValue < Float.MinValue then
                throw new BsonInvalidOperationException(
                  s"Double value $doubleValue is below Float.MinValue (${Float.MinValue}). " +
                    s"Cannot safely convert to Float without overflow."
                )
              else
                // Safe conversion within Float range
                // Note: Precision loss may still occur for values requiring more than 24 bits of mantissa
                doubleValue.toFloat.asInstanceOf[V]
              end if
            case BsonType.INT32 if clazz == classOf[Byte] || clazz == classOf[java.lang.Byte] =>
              reader.readInt32().toByte.asInstanceOf[V]
            case BsonType.INT32 if clazz == classOf[Short] || clazz == classOf[java.lang.Short] =>
              reader.readInt32().toShort.asInstanceOf[V]
            case BsonType.INT32 if clazz == classOf[Char] || clazz == classOf[java.lang.Character] =>
              reader.readInt32().toChar.asInstanceOf[V]
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
