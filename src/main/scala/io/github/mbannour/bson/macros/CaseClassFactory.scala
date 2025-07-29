package io.github.mbannour.bson.macros

import scala.quoted.*

object CaseClassFactory:

  /** Instantiates a case class of type T using the provided field data. Field names can be optionally overridden via annotations.
    */
  private[mbannour] inline def getInstance[T](fieldData: Map[String, Any]): T =
    ${ getInstanceImpl[T]('fieldData) }

  private[mbannour] def getInstanceImpl[T: Type](fieldData: Expr[Map[String, Any]])(using Quotes): Expr[T] =
    import quotes.reflect.*

    val mainTypeRepr = TypeRepr.of[T]
    val mainTypeSymbol = mainTypeRepr.typeSymbol
    if !mainTypeSymbol.flags.is(Flags.Case) then
      report.errorAndAbort(s"${mainTypeSymbol.name} is not a case class, and cannot be instantiated this way.")

    val constructorParams = mainTypeSymbol.primaryConstructor.paramSymss.flatten

    val fieldExprs: List[Expr[Any]] = constructorParams.map { param =>
      val paramName: String = param.name
      val paramNameExpr: Expr[String] = Expr(paramName)
      val paramType: TypeRepr = param.tree match
        case vd: ValDef => vd.tpt.tpe
        case other      => report.errorAndAbort(s"Unexpected tree for parameter $paramName: ${other.show}")

      val keyToUse: Expr[String] =
        AnnotationName.findAnnotationValue[T](Expr(paramName)) match
          case '{ Some($annotationValue: String) } => annotationValue
          case '{ None }                           => Expr(paramName)

      val paramTypeShowExpr: Expr[String] = Expr(paramType.show)

      paramType.asType match
        case '[Option[t]] =>
          '{
            $fieldData.get($keyToUse) match
              case Some(value) => Option(value.asInstanceOf[t])
              case None        => None
          }

        case '[nestedT] if paramType.typeSymbol.flags.is(Flags.Case) =>
          '{
            $fieldData.getOrElse(
              $keyToUse,
              throw new RuntimeException("Field: " + $keyToUse + " not found")
            ) match
              case instance: nestedT @unchecked     => instance
              case map: Map[String, Any] @unchecked => getInstance[nestedT](map)
              case other =>
                throw new RuntimeException("Unexpected type for field " + other.getClass)
          }

        case '[nestedT] if paramType.typeSymbol.flags.is(Flags.Enum) =>
          val enumCompanionName: Expr[String] =
            Expr(paramType.typeSymbol.companionModule.fullName)
          '{
            $fieldData.get($keyToUse) match
              case Some(value: String @unchecked) =>
                try
                  val enumClass = Class.forName($enumCompanionName)
                  val method = enumClass.getMethod("valueOf", classOf[String])
                  method.invoke(enumClass, value).asInstanceOf[nestedT]
                catch
                  case ex: Exception =>
                    throw new RuntimeException("Error decoding enum field '" + $keyToUse + "': " + ex.getMessage, ex)
              case Some(intVal: Int @unchecked) =>
                try
                  val enumClass = Class.forName($enumCompanionName)
                  val values = enumClass.getMethod("values").invoke(enumClass).asInstanceOf[Array[Object]]

                  // ---------- 1) try ordinal ----------
                  if intVal >= 0 && intVal < values.length then values(intVal).asInstanceOf[nestedT]
                  else
                    // ---------- 2) try custom `code` ----------
                    val matched = values.find { v =>
                      try v.getClass.getMethod("code").invoke(v) == intVal
                      catch case _: NoSuchMethodException => false
                    }

                    matched
                      .map(_.asInstanceOf[nestedT])
                      .getOrElse {
                        throw new RuntimeException(
                          "No enum value with ordinal " + intVal + " or code " + intVal + " for field '" + $keyToUse + "'"
                        )
                      }
                  end if
                catch
                  case ex: Exception =>
                    throw new RuntimeException(
                      "Error decoding enum field '" + $keyToUse + "' with integer " + intVal + ": " + ex.getMessage,
                      ex
                    )
              case Some(enumValue: nestedT @unchecked) =>
                enumValue
              case Some(null) => null

              case other =>
                throw new RuntimeException("Unexpected value type for enum field '" + $keyToUse + "': " + other.getClass)
              case None =>
                throw new RuntimeException("Missing enum field: " + $keyToUse)
          }

        case '[nestedT] =>
          '{
            val rawValue = $fieldData.getOrElse(
              $keyToUse,
              throw new RuntimeException("Missing field: " + $keyToUse)
            )
            try rawValue.asInstanceOf[nestedT]
            catch
              case ex: ClassCastException =>
                throw new RuntimeException(
                  "Error casting field " + $paramNameExpr + ". Expected: " + $paramTypeShowExpr +
                    ", Actual: " + rawValue.getClass.getName,
                  ex
                )
            end try
          }
      end match
    }

    val instance = Apply(
      Select(New(TypeIdent(mainTypeSymbol)), mainTypeSymbol.primaryConstructor),
      fieldExprs.map(_.asTerm)
    ).asExprOf[T]

    instance
  end getInstanceImpl
end CaseClassFactory
