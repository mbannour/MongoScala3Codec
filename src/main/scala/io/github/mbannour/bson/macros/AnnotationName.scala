package io.github.mbannour.bson.macros

import org.mongodb.scala.bson.annotations.BsonProperty

import scala.quoted.*

object AnnotationName:

  /** Inline helper to retrieve the annotation value for a given field name of type T.
    *
    * @param fieldName
    *   the name of the field to check for a @BsonProperty annotation override.
    * @tparam T
    *   the target case class type.
    * @return
    *   an Option[String] containing the annotation value if present.
    */
  private[mbannour] inline def invokeFindAnnotationValue[T](fieldName: String): Option[String] =
    ${ findAnnotationValue[T]('fieldName) }

  /** Retrieves the value provided in the @BsonProperty annotation for a constructor parameter in type T.
    *
    * This method collects all constructor parameters of T annotated with @BsonProperty, builds a map from the parameter name to its
    * annotation value, and then looks up the provided field name.
    *
    * @param fieldName
    *   an Expr[String] representing the field name for which to find the annotation value.
    * @tparam T
    *   the target case class type.
    * @return
    *   an Expr[Option[String]] containing the annotation value if one is found.
    */
  private[mbannour] def findAnnotationValue[T: Type](using Quotes)(fieldName: Expr[String]): Expr[Option[String]] =
    import quotes.reflect.*

    // Obtain the type representation of T and the symbol for the BsonProperty annotation.
    val tpe = TypeRepr.of[T]
    val bsonPropertySymbol = TypeRepr.of[BsonProperty].typeSymbol

    // Collect all constructor parameters annotated with @BsonProperty along with their annotation values.
    val annotatedParams: Seq[(String, String)] =
      tpe.typeSymbol.primaryConstructor.paramSymss.flatten.collect {
        case param if param.hasAnnotation(bsonPropertySymbol) =>
          val paramName = param.name
          val annotationTree = param.getAnnotation(bsonPropertySymbol).get
          val annotationValue = annotationTree match
            case Apply(_, List(Literal(StringConstant(value)))) => value
            case other =>
              throw new MatchError(s"Unexpected annotation expression: ${other.show}")
          (paramName, annotationValue)
      }

    // Build a map from parameter names to their annotated values.
    val annotationsMap: Map[String, String] = annotatedParams.toMap

    // Retrieve the literal field name from the provided Expr.
    val literalFieldName = fieldName.valueOrAbort

    // Look up the field name in the annotations map.
    annotationsMap.get(literalFieldName) match
      case Some(value) => '{ Some(${ Expr(value) }) }
      case None        => '{ None }
  end findAnnotationValue
end AnnotationName
