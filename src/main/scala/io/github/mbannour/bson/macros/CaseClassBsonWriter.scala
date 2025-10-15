package io.github.mbannour.bson.macros

import scala.quoted._
import scala.reflect.ClassTag

import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry

object CaseClassBsonWriter:

  private[mbannour] inline def writeCaseClassData[T](
      className: String,
      writer: BsonWriter,
      value: T,
      encoderContext: EncoderContext,
      encodeNone: Boolean,
      registry: CodecRegistry
  ): Unit =
    ${ writeCaseClassDataImpl[T]('className, 'writer, 'value, 'encoderContext, 'encodeNone, 'registry) }

  private[mbannour] def writeCaseClassDataImpl[T: Type](
      className: Expr[String],
      writer: Expr[BsonWriter],
      value: Expr[T],
      encoderContext: Expr[EncoderContext],
      encodeNone: Expr[Boolean],
      registry: Expr[CodecRegistry]
  )(using Quotes): Expr[Unit] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val typeSymbol = tpe.typeSymbol

    if !typeSymbol.flags.is(Flags.Case) then report.errorAndAbort(s"${typeSymbol.name} is not a case class.")

    val fieldWrites = typeSymbol.caseFields.map { field =>

      val res = AnnotationName.findAnnotationValue[T](Expr(field.name))

      val fieldName: Expr[String] = res match
        case '{ Some($annotationValue: String) } => annotationValue
        case '{ None }                           => Expr(field.name)

      val fieldValueExpr = Select(value.asTerm, field).asExpr

      field.tree.asInstanceOf[ValDef].tpt.tpe.asType match
        case '[String] =>
          '{
            $writer.writeString(${ fieldName }, $fieldValueExpr.asInstanceOf[String])
          }
        case '[Int] =>
          '{ $writer.writeInt32(${ fieldName }, $fieldValueExpr.asInstanceOf[Int]) }
        case '[Double] =>
          '{ $writer.writeDouble(${ fieldName }, $fieldValueExpr.asInstanceOf[Double]) }
        case '[Float] =>
          '{ $writer.writeDouble(${ fieldName }, $fieldValueExpr.asInstanceOf[Float].toDouble) }
        case '[Boolean] =>
          '{ $writer.writeBoolean(${ fieldName }, $fieldValueExpr.asInstanceOf[Boolean]) }
        case '[Long] =>
          '{ $writer.writeInt64(${ fieldName }, $fieldValueExpr.asInstanceOf[Long]) }
        case '[Byte] =>
          '{ $writer.writeInt32(${ fieldName }, $fieldValueExpr.asInstanceOf[Byte].toInt) }
        case '[Short] =>
          '{ $writer.writeInt32(${ fieldName }, $fieldValueExpr.asInstanceOf[Short].toInt) }
        case '[Char] =>
          '{ $writer.writeInt32(${ fieldName }, $fieldValueExpr.asInstanceOf[Char].toInt) }
        case '[java.util.UUID] =>
          '{ $writer.writeString(${ fieldName }, $fieldValueExpr.asInstanceOf[java.util.UUID].toString) }
        case '[Option[t]] =>
          '{
            $fieldValueExpr.asInstanceOf[Option[t]] match
              case Some(innerValue) =>
                $writer.writeName(${ fieldName })
                ${ writeOptionField(Type.of[t], 'innerValue, writer, encoderContext, encodeNone, registry) }
              case None =>
                if $encodeNone then $writer.writeNull(${ fieldName })
          }
        case '[Map[String, t]] =>
          '{
            $writer.writeStartDocument(${ fieldName })
            $fieldValueExpr.asInstanceOf[Map[String, t]].foreach { case (key, value) =>
              $writer.writeName(key)
              ${
                writeOptionField(Type.of[t], 'value, writer, encoderContext, encodeNone, registry)
              }
            }
            $writer.writeEndDocument()
          }
        case '[Iterable[t]] =>
          '{
            $writer.writeStartArray(${ fieldName })
            $fieldValueExpr.asInstanceOf[Iterable[t]].foreach { item =>
              ${
                writeOptionField(Type.of[t], 'item, writer, encoderContext, encodeNone, registry)
              }
            }
            $writer.writeEndArray()
          }
        case nestedType =>
          val nestedTypeRepr = nestedType match
            case t: scala.quoted.Type[?] =>
              val convertedTypeRepr = TypeRepr.of(using t)
              convertedTypeRepr
            case _ =>
              report.errorAndAbort("nestedType is not a scala.quoted.Type")
          nestedTypeRepr.asType match
            case '[nt] =>
              '{
                val fieldValue = $fieldValueExpr.asInstanceOf[nt]
                // For sealed traits, use the actual runtime class
                val actualClass = fieldValue.getClass.asInstanceOf[Class[nt]]
                try
                  val codec = $registry.get(actualClass)
                  $writer.writeName(${ fieldName })
                  codec.encode($writer, fieldValue, $encoderContext)
                catch
                  case e: org.bson.codecs.configuration.CodecConfigurationException =>
                    throw new IllegalArgumentException(s"No codec found for type: " + actualClass.getName, e)
              }
          end match
      end match
    }

    Expr.block(fieldWrites.toList, '{ () })
  end writeCaseClassDataImpl

  def writeOptionField[T: Type](
      fieldType: Type[T],
      value: Expr[Any],
      writer: Expr[BsonWriter],
      encoderContext: Expr[EncoderContext],
      encodeNone: Expr[Boolean],
      registry: Expr[CodecRegistry]
  )(using
      Quotes
  ): Expr[Unit] =
    import quotes.reflect.*

    def getClassForType[T: Type](using Quotes): Expr[Class[T]] =
      Expr.summon[ClassTag[T]] match
        case Some(classTagExpr) => '{ $classTagExpr.runtimeClass.asInstanceOf[Class[T]] }
        case None               => report.errorAndAbort(s"Could not find ClassTag for type: ${Type.show[T]}")

    TypeRepr.of[T] match
      case t if t =:= TypeRepr.of[String] =>
        '{ $writer.writeString($value.asInstanceOf[String]) }
      case t if t =:= TypeRepr.of[Int] =>
        '{ $writer.writeInt32($value.asInstanceOf[Int]) }
      case t if t =:= TypeRepr.of[Double] =>
        '{ $writer.writeDouble($value.asInstanceOf[Double]) }
      case t if t =:= TypeRepr.of[Boolean] =>
        '{ $writer.writeBoolean($value.asInstanceOf[Boolean]) }
      case t if t =:= TypeRepr.of[Long] =>
        '{ $writer.writeInt64($value.asInstanceOf[Long]) }
      case t if t =:= TypeRepr.of[Byte] =>
        '{ $writer.writeInt32($value.asInstanceOf[Byte].toInt) }
      case t if t =:= TypeRepr.of[Short] =>
        '{ $writer.writeInt32($value.asInstanceOf[Short].toInt) }
      case t if t =:= TypeRepr.of[Char] =>
        '{ $writer.writeInt32($value.asInstanceOf[Char].toInt) }
      case t =>
        t.asType match
          case '[nt] =>
            '{
              val clazz = ${ getClassForType[nt] }
              try
                val codec = $registry.get(clazz)
                codec.encode($writer, $value.asInstanceOf[nt], $encoderContext)
              catch
                case e: org.bson.codecs.configuration.CodecConfigurationException =>
                  throw new IllegalArgumentException(s"No codec found for type: " + clazz.getName, e)
            }
    end match
  end writeOptionField
end CaseClassBsonWriter
