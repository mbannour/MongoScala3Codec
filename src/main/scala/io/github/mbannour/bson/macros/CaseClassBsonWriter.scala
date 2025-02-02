package io.github.mbannour.bson.macros

import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry

import scala.quoted.*
import scala.reflect.ClassTag

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

    def getClassForType[T: Type](using Quotes): Expr[Class[T]] =
      Expr.summon[ClassTag[T]] match
        case Some(classTagExpr) => '{ $classTagExpr.runtimeClass.asInstanceOf[Class[T]] }
        case None               => report.errorAndAbort(s"Could not find ClassTag for type: ${Type.show[T]}")

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
        case '[Boolean] =>
          '{ $writer.writeBoolean(${ fieldName }, $fieldValueExpr.asInstanceOf[Boolean]) }
        case '[Long] =>
          '{ $writer.writeInt64(${ fieldName }, $fieldValueExpr.asInstanceOf[Long]) }
        case '[Option[t]] =>
          '{
            $fieldValueExpr.asInstanceOf[Option[t]] match
              case Some(innerValue) =>
                ${
                  if TypeRepr.of[t].typeSymbol.flags.is(Flags.Case) then
                    '{
                      $writer.writeName(${ fieldName })
                      $writer.writeStartDocument()
                      writeCaseClassData(
                        ${ Expr(Type.show[t]) },
                        $writer,
                        innerValue.asInstanceOf[t],
                        $encoderContext,
                        $encodeNone,
                        $registry
                      )
                      $writer.writeEndDocument()
                    }
                  else if TypeRepr.of[t] <:< TypeRepr.of[Iterable[?]] then
                    // t is an Iterable, so extract the inner type and handle it accordingly.
                    TypeRepr.of[t] match
                      case AppliedType(_, List(innerType)) =>
                        innerType.asType match
                          case '[x] =>
                            '{
                              $writer.writeStartArray(${ fieldName })
                              innerValue.asInstanceOf[Iterable[x]].foreach { item =>
                                ${
                                  if TypeRepr.of[x].typeSymbol.flags.is(Flags.Case) then
                                    '{

                                      $writer.writeStartDocument()
                                      writeCaseClassData(
                                        ${ Expr(Type.show[x]) },
                                        $writer,
                                        item.asInstanceOf[x],
                                        $encoderContext,
                                        $encodeNone,
                                        $registry
                                      )
                                      $writer.writeEndDocument()
                                    }
                                  else writeOptionField(Type.of[x], 'item, writer, encoderContext, encodeNone, registry)
                                }
                              }
                              $writer.writeEndArray()
                            }
                      case _ =>
                        report.errorAndAbort("Could not extract inner type for Iterable")
                  else
                    '{
                      $writer.writeName(${ fieldName })
                      ${ writeOptionField(Type.of[t], 'innerValue, writer, encoderContext, encodeNone, registry) }
                    }
                }
              case None =>
                if $encodeNone then $writer.writeNull(${ fieldName })
          }
        case '[Iterable[t]] =>
          '{
            $writer.writeStartArray(${ fieldName })
            $fieldValueExpr.asInstanceOf[Iterable[t]].foreach { item =>
              ${
                if TypeRepr.of[t] <:< TypeRepr.of[Option[?]] then
                  TypeRepr.of[t] match
                    case AppliedType(_, List(innerOptType)) =>
                      innerOptType.asType match
                        case '[u] =>
                          '{
                            // Pattern–match on the Option value
                            item.asInstanceOf[Option[u]] match
                              case Some(innerValue) =>
                                ${
                                  if TypeRepr.of[u].typeSymbol.flags.is(Flags.Case) then
                                    '{
                                      $writer.writeStartDocument()
                                      writeCaseClassData(
                                        ${ Expr(Type.show[u]) },
                                        $writer,
                                        innerValue.asInstanceOf[u],
                                        $encoderContext,
                                        $encodeNone,
                                        $registry
                                      )
                                      $writer.writeEndDocument()
                                    }
                                  else writeOptionField(Type.of[u], 'innerValue, writer, encoderContext, encodeNone, registry)
                                }
                              case None =>
                                if $encodeNone then $writer.writeNull(${ fieldName })
                          }
                    case _ =>
                      report.errorAndAbort("Could not extract inner type for Option in Iterable")
                else if TypeRepr.of[t].typeSymbol.flags.is(Flags.Case) then
                  '{
                    $writer.writeStartDocument()
                    writeCaseClassData(
                      ${ Expr(Type.show[t]) },
                      $writer,
                      item.asInstanceOf[t],
                      $encoderContext,
                      $encodeNone,
                      $registry
                    )
                    $writer.writeEndDocument()
                  }
                else writeOptionField(Type.of[t], 'item, writer, encoderContext, encodeNone, registry)
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

          if nestedTypeRepr.typeSymbol.flags.is(Flags.Case) then
            nestedTypeRepr.asType match
              case '[nt] =>
                '{
                  $writer.writeName(${ fieldName })
                  $writer.writeStartDocument()
                  CaseClassBsonWriter.writeCaseClassData(
                    ${ fieldName },
                    $writer,
                    $fieldValueExpr.asInstanceOf[nt],
                    $encoderContext,
                    $encodeNone,
                    $registry
                  )
                  $writer.writeEndDocument()
                }
              case _ =>
                report.errorAndAbort(s"Unsupported nested case class type: ${nestedTypeRepr.show}")
          else
            nestedTypeRepr.asType match
              case '[nt] =>
                '{
                  val clazz = ${ getClassForType[nt] }

                  try
                    val codec = $registry.get(clazz)
                    $writer.writeName(${ fieldName })
                    codec.encode($writer, $fieldValueExpr.asInstanceOf[nt], $encoderContext)
                  catch
                    case _: org.bson.codecs.configuration.CodecConfigurationException =>
                      throw new IllegalArgumentException(s"No codec found for type: $clazz")
                }
          end if
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
      case t if t <:< TypeRepr.of[Product] && t.typeSymbol.flags.is(Flags.Case) =>
        '{ writeCaseClassData(${ Expr(Type.show[T]) }, $writer, $value.asInstanceOf[T], $encoderContext, $encodeNone, $registry) }
      case _ =>
        '{ $writer.writeString($value.toString) }
    end match
  end writeOptionField
end CaseClassBsonWriter
