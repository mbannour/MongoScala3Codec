package io.github.mbannour.bson.macros

import org.mongodb.scala.bson.annotations.BsonProperty

import scala.quoted.*

object AnnotationName:

  inline def invokeFindAnnotationValue[T](fieldName: String): Option[String] = ${
    AnnotationName.findAnnotationValue[T]('fieldName)
  }

  def findAnnotationValue[T: Type](using Quotes)(fieldName: Expr[String]): Expr[Option[String]] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val annot = TypeRepr.of[BsonProperty].typeSymbol

    val tuples: Seq[(String, String)] = tpe.typeSymbol.primaryConstructor.paramSymss.flatten
      .collect {
        case sym if sym.hasAnnotation(annot) =>
          val fieldName = sym.name

          val annotation = sym.getAnnotation(annot).get

          // Extract the value of the annotation using reflection
          val annotValue = annotation match
            case Apply(_, List(Literal(StringConstant(value)))) =>
              value
            case other =>
              throw new MatchError(s"Unexpected annotation expression: ${other.show}")

          (fieldName, annotValue)
      }

    val map: Map[String, String] = tuples.toMap

    val fieldNameValue = fieldName.valueOrAbort

    map.get(fieldNameValue) match
      case Some(value) =>
        '{ Some(${ Expr(value) }) }
      case None =>
        '{ None }
  end findAnnotationValue
end AnnotationName
