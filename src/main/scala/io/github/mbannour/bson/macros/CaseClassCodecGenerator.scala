package io.github.mbannour.bson.macros

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, Encoder, EncoderContext}
import org.bson.*

import java.util.UUID
import scala.collection.mutable
import scala.quoted.*
import scala.reflect.ClassTag

/** Macro-based codec generator for BSON serialization and deserialization of case classes.
  */
object CaseClassCodecGenerator:

  /** Entry point for generating a BSON codec for a specific type `T`. Validates that `T` is a case class at compile-time.
    *
    * @param codecRegistry
    *   CodecRegistry used for nested fields.
    * @tparam T
    *   Type for which the codec is generated.
    * @return
    *   A BSON codec instance for type `T`.
    */
  inline def generateCodec[T](codecRegistry: CodecRegistry, encodeNone: Boolean)(using classTag: ClassTag[T]): Codec[T] =
    ${ generateCodecImpl[T]('codecRegistry, 'encodeNone, 'classTag) }

  def generateCodecImpl[T: Type](codecRegistry: Expr[CodecRegistry], encodeNone: Expr[Boolean], classTag: Expr[ClassTag[T]])(using
      Quotes
  ): Expr[Codec[T]] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T].typeSymbol
    if !tpe.flags.is(Flags.Case) then report.errorAndAbort(s"${tpe.name} is not a case class and cannot be used with TestCodec.")

    '{
      new Codec[T]:

        private val encoderClass: Class[T] = $classTag.runtimeClass.asInstanceOf[Class[T]]

        val classFieldName = "_t"
        val caseClassesMap: Map[String, Class[?]] = CaseClassMapper.caseClassMap[T]
        val classToCaseClassMap: Map[Class[?], Boolean] = ClassToCaseFlagMap.classToCaseClassMap[T]
        val classFieldTypeArgsMap: Map[String, Map[String, List[Class[?]]]] = CaseClassFieldMapper.createClassFieldTypeArgsMap[T]
        lazy val caseClassesMapInv: Map[Class[?], String] = caseClassesMap.map(_.swap)
        lazy val registry: CodecRegistry = CodecRegistries.fromRegistries(
          $codecRegistry,
          CodecRegistries.fromCodecs(this)
        )

        lazy val hasClassFieldName: Boolean = caseClassesMap.size > 1

        def getInstance(className: String, fieldsData: Map[String, Any]): T =
          CaseClassFactory.getInstance[T](fieldsData)

        override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit =
          if value == null then throw new BsonInvalidOperationException(s"Invalid value for $encoderClass found a `null` value.")
          writeValue(writer, value, encoderContext)

        protected def writeValue[V](writer: BsonWriter, value: V, encoderContext: EncoderContext): Unit =
          writer.writeStartDocument()
          val clazz = value.getClass

          caseClassesMapInv.get(clazz) match
            case Some(className) =>
              CaseClassBsonWriter.writeCaseClassData(className, writer, value.asInstanceOf[T], encoderContext, $encodeNone, registry)
            case None =>
              val codec = registry.get(clazz).asInstanceOf[Encoder[V]]
              encoderContext.encodeWithChildContext(codec, writer, value)
          writer.writeEndDocument() // End the document
        end writeValue

        override def decode(reader: BsonReader, decoderContext: DecoderContext): T =

          val className = getClassName(reader, decoderContext)

          val fieldTypeArgsMap: Map[String, List[Class[?]]] =
            classFieldTypeArgsMap.getOrElse(className, Map.empty)

          val map = mutable.Map[String, Any]()
          reader.readStartDocument()

          while reader.readBsonType != BsonType.END_OF_DOCUMENT do
            val name = reader.readName
            val typeArgs =
              if name == classFieldName then List(classOf[String])
              else fieldTypeArgsMap.getOrElse(name, List.empty)

            if typeArgs.isEmpty then reader.skipValue()
            else
              val value = readValue(reader, decoderContext, typeArgs.head, typeArgs.tail, fieldTypeArgsMap)
              map += (name -> value)
          end while

          reader.readEndDocument()
          val instance = getInstance(className, map.toMap)
          instance
        end decode

        val primitiveToBoxed: Map[Class[?], Class[?]] = Map(
          classOf[scala.Int] -> classOf[java.lang.Integer],
          classOf[scala.Long] -> classOf[java.lang.Long],
          classOf[scala.Double] -> classOf[java.lang.Double],
          classOf[scala.Float] -> classOf[java.lang.Float],
          classOf[scala.Boolean] -> classOf[java.lang.Boolean],
          classOf[scala.Short] -> classOf[java.lang.Short],
          classOf[scala.Byte] -> classOf[java.lang.Byte],
          classOf[scala.Char] -> classOf[java.lang.Character]
        )

        protected def readValue[V](
            reader: BsonReader,
            decoderContext: DecoderContext,
            clazz: Class[V],
            typeArgs: List[Class[?]],
            fieldTypeArgsMap: Map[String, List[Class[?]]]
        ): V =

          val currentType = reader.getCurrentBsonType

          currentType match
            case BsonType.DOCUMENT =>
              readDocument(reader, decoderContext, clazz, typeArgs, fieldTypeArgsMap)

            case BsonType.ARRAY =>
              readArray(reader, decoderContext, clazz, typeArgs, fieldTypeArgsMap)

            case BsonType.NULL =>
              reader.readNull()
              null.asInstanceOf[V] // scalastyle:ignore

            case BsonType.STRING if clazz == classOf[UUID] =>
              val stringValue = reader.readString()
              try UUID.fromString(stringValue).asInstanceOf[V]
              catch
                case _: IllegalArgumentException =>
                  throw new IllegalArgumentException(s"Invalid UUID string format: $stringValue")

            case otherType =>
              val effectiveClass = primitiveToBoxed.getOrElse(clazz, clazz).asInstanceOf[Class[V]]
              val codec = registry.get(effectiveClass)

              val decodedValue: V = codec.decode(reader, decoderContext)
              decodedValue
          end match
        end readValue

        protected def readArray[V](
            reader: BsonReader,
            decoderContext: DecoderContext,
            clazz: Class[V],
            typeArgs: List[Class[?]],
            fieldTypeArgsMap: Map[String, List[Class[?]]]
        ): V =

          if typeArgs.isEmpty then
            throw new BsonInvalidOperationException(
              s"Invalid Bson format for '${clazz.getSimpleName}'. Found a list but there is no type data."
            )
          reader.readStartArray()
          val list = mutable.ListBuffer[Any]()
          while reader.readBsonType ne BsonType.END_OF_DOCUMENT do
            list.append(readValue(reader, decoderContext, typeArgs.head, typeArgs.tail, fieldTypeArgsMap))
          reader.readEndArray()
          if classOf[Set[?]].isAssignableFrom(clazz) then list.toSet.asInstanceOf[V]
          else if classOf[Vector[?]].isAssignableFrom(clazz) then list.toVector.asInstanceOf[V]
          else list.toList.asInstanceOf[V]
        end readArray

        protected def readDocument[V](
            reader: BsonReader,
            decoderContext: DecoderContext,
            clazz: Class[V],
            typeArgs: List[Class[?]],
            fieldTypeArgsMap: Map[String, List[Class[?]]]
        ): V =
          if classToCaseClassMap.getOrElse(clazz, false) || typeArgs.isEmpty then registry.get(clazz).decode(reader, decoderContext)
          else
            val map = mutable.Map[String, Any]()
            reader.readStartDocument()
            while reader.readBsonType ne BsonType.END_OF_DOCUMENT do
              val name = reader.readName
              val fieldClazzTypeArgs = fieldTypeArgsMap.getOrElse(name, typeArgs)
              if fieldClazzTypeArgs.isEmpty then reader.skipValue()
              else map += (name -> readValue(reader, decoderContext, fieldClazzTypeArgs.head, fieldClazzTypeArgs.tail, fieldTypeArgsMap))
            reader.readEndDocument()
            map.toMap.asInstanceOf[V]

        protected def getClassName(reader: BsonReader, decoderContext: DecoderContext): String =
          if hasClassFieldName then

            @scala.annotation.tailrec
            def readOptionalClassName(): Option[String] =
              val currentType = reader.readBsonType

              if currentType == BsonType.END_OF_DOCUMENT then None
              else
                val name = reader.readName

                if name == classFieldName then
                  val className = $codecRegistry.get(classOf[String]).decode(reader, decoderContext)
                  Some(className)
                else
                  reader.skipValue()
                  readOptionalClassName()
              end if
            end readOptionalClassName

            val mark: BsonReaderMark = reader.getMark()

            reader.readStartDocument()

            val optionalClassName: Option[String] = readOptionalClassName()
            mark.reset()

            val className = optionalClassName.getOrElse {
              throw new BsonInvalidOperationException(s"Could not decode sealed case class. Missing '$classFieldName' field.")
            }

            if !caseClassesMap.contains(className) then
              throw new BsonInvalidOperationException(s"Could not decode sealed case class, unknown class $className.")

            className
          else caseClassesMap.head._1

        override def getEncoderClass: Class[T] =
          encoderClass
    }
  end generateCodecImpl
end CaseClassCodecGenerator
