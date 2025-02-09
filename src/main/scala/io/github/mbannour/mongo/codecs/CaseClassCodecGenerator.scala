package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, Encoder, EncoderContext}
import io.github.mbannour.bson.macros.*
import org.bson.{BsonInvalidOperationException, BsonReader, BsonReaderMark, BsonType, BsonWriter}

import java.util.UUID
import scala.collection.mutable
import scala.quoted.*
import scala.reflect.ClassTag

/** Macro-based codec generator for BSON serialization/deserialization of case classes, supporting nested and sealed hierarchies.
  */
object CaseClassCodecGenerator:

  /** Generates a BSON codec for the given type `T`.
    *
    * @param encodeNone
    *   Flag to indicate whether to encode `None` values in optional fields.
    * @param codecRegistry
    *   The base CodecRegistry to be used for nested codec lookups.
    * @tparam T
    *   The case class type for which the codec is generated.
    * @return
    *   A BSON Codec[T] instance.
    */
  private[codecs] inline def generateCodec[T](encodeNone: Boolean, codecRegistry: CodecRegistry)(using
      classTag: ClassTag[T]
  ): Codec[T] =
    ${ generateCodecImpl[T]('encodeNone, 'codecRegistry, 'classTag) }

  private def generateCodecImpl[T: Type](
      encodeNone: Expr[Boolean],
      baseRegistry: Expr[CodecRegistry],
      classTag: Expr[ClassTag[T]]
  )(using Quotes): Expr[Codec[T]] =
    import quotes.reflect.*

    // Ensure T is a case class (or a sealed hierarchy of case classes)
    val tpeSym = TypeRepr.of[T].typeSymbol
    if !tpeSym.flags.is(Flags.Case) then
      report.errorAndAbort(s"${tpeSym.name} is not a case class and cannot be used with this codec generator.")

    '{
      new Codec[T]:
        // The runtime class for type T
        private val encoderClass: Class[T] = $classTag.runtimeClass.asInstanceOf[Class[T]]
        // Discriminator field used for sealed hierarchies
        private val discriminatorField: String = "_t"

        // Maps from discriminator values to classes and vice versa.
        private val caseClassesMap: Map[String, Class[?]] = CaseClassMapper.caseClassMap[T]
        private val classToCaseClassMap: Map[Class[?], Boolean] = ClassToCaseFlagMap.classToCaseClassMap[T]
        private val fieldTypeArgsMapByClass: Map[String, Map[String, List[Class[?]]]] = CaseClassFieldMapper.createClassFieldTypeArgsMap[T]
        private lazy val caseClassesMapInv: Map[Class[?], String] = caseClassesMap.map(_.swap)

        // Compose the registry using the provided baseRegistry and the codec for this type.
        private val registry: CodecRegistry = CodecRegistries.fromRegistries(
          $baseRegistry,
          CodecRegistries.fromCodecs(this)
        )

        // Indicates if a discriminator is required (when more than one case class is present)
        private lazy val hasDiscriminator: Boolean = caseClassesMap.size > 1

        private def getInstance(discriminator: String, fieldsData: Map[String, Any]): T =
          CaseClassFactory.getInstance[T](fieldsData)

        override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
          if value == null then throw new BsonInvalidOperationException(s"Invalid value for $encoderClass: found a null value.")
          else writeValue(writer, value, encoderContext)

        /** Writes a value of type V to the BSON writer. */
        private def writeValue[V](writer: BsonWriter, value: V, encoderContext: EncoderContext): Unit =
          writer.writeStartDocument()
          val clazz = value.getClass
          caseClassesMapInv.get(clazz) match
            case Some(discriminator) =>
              // For case classes, delegate to the helper writer with the discriminator.
              CaseClassBsonWriter.writeCaseClassData(discriminator, writer, value.asInstanceOf[T], encoderContext, $encodeNone, registry)
            case None =>
              // Fallback: use the codec from the registry.
              val codec = registry.get(clazz).asInstanceOf[Encoder[V]]
              encoderContext.encodeWithChildContext(codec, writer, value)
          end match
          writer.writeEndDocument()
        end writeValue

        override def decode(reader: BsonReader, decoderContext: DecoderContext): T =
          val discriminator = extractDiscriminator(reader, decoderContext)
          val fieldTypeArgs: Map[String, List[Class[?]]] = fieldTypeArgsMapByClass.getOrElse(discriminator, Map.empty)
          val fieldsData = mutable.Map.empty[String, Any]
          reader.readStartDocument()
          while reader.readBsonType != BsonType.END_OF_DOCUMENT do
            val name = reader.readName
            val typeArgs =
              if name == discriminatorField then List(classOf[String])
              else fieldTypeArgs.getOrElse(name, List.empty)
            if typeArgs.isEmpty then reader.skipValue()
            else
              val value = readValue(reader, decoderContext, typeArgs.head, typeArgs.tail, fieldTypeArgs)
              fieldsData += (name -> value)
          end while
          reader.readEndDocument()
          getInstance(discriminator, fieldsData.toMap)
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
              null.asInstanceOf[V]
            case BsonType.STRING if clazz == classOf[UUID] =>
              val stringValue = reader.readString()
              try UUID.fromString(stringValue).asInstanceOf[V]
              catch
                case _: IllegalArgumentException =>
                  throw new IllegalArgumentException(s"Invalid UUID string format: $stringValue")
            case _ =>
              val effectiveClass = primitiveToBoxed.getOrElse(clazz, clazz).asInstanceOf[Class[V]]
              val codec = registry.get(effectiveClass)
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
            throw new BsonInvalidOperationException(s"Invalid BSON format for '${clazz.getSimpleName}'. Found an array but no type data.")
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
          if classToCaseClassMap.getOrElse(clazz, false) || typeArgs.isEmpty then registry.get(clazz).decode(reader, decoderContext)
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

        /** Extracts the discriminator (type identifier) from the BSON document. */
        private def extractDiscriminator(reader: BsonReader, decoderContext: DecoderContext): String =
          if hasDiscriminator then
            @scala.annotation.tailrec
            def readOptionalDiscriminator(): Option[String] =
              val currentType = reader.readBsonType
              if currentType == BsonType.END_OF_DOCUMENT then None
              else
                val name = reader.readName
                if name == discriminatorField then Some(registry.get(classOf[String]).decode(reader, decoderContext))
                else
                  reader.skipValue()
                  readOptionalDiscriminator()
            end readOptionalDiscriminator
            val mark: BsonReaderMark = reader.getMark()
            reader.readStartDocument()
            val maybeDiscriminator = readOptionalDiscriminator()
            mark.reset()
            maybeDiscriminator.getOrElse {
              throw new BsonInvalidOperationException(s"Missing discriminator field '$discriminatorField' for sealed case class.")
            }
          else
            // For non-sealed hierarchies, return the first available discriminator.
            caseClassesMap.head._1

        override def getEncoderClass: Class[T] = encoderClass
    }
  end generateCodecImpl
end CaseClassCodecGenerator
