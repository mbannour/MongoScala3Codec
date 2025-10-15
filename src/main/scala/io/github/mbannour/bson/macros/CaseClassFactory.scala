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

    // Get the companion object to access default value methods
    val companionSymbol = mainTypeSymbol.companionModule
    val companionRef = Ref(companionSymbol)

    val fieldExprs: List[Expr[Any]] = constructorParams.zipWithIndex.map { case (param, index) =>
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

      // Find default value method more robustly
      // Instead of constructing the method name with fragile string interpolation,
      // we search for methods that match the default parameter pattern
      val defaultValueOpt: Option[Expr[Any]] =
        findDefaultValueMethod(companionSymbol, companionRef, index)

      paramType.asType match
        case '[Option[t]] =>
          '{
            $fieldData.get($keyToUse) match
              case Some(null)  => None
              case Some(value) => Option(value.asInstanceOf[t])
              case None        => None
          }

        case '[nestedT] if paramType.typeSymbol.flags.is(Flags.Case) =>
          defaultValueOpt match
            case Some(defaultValue) =>
              '{
                $fieldData.get($keyToUse) match
                  case Some(instance: nestedT @unchecked)     => instance
                  case Some(map: Map[String, Any] @unchecked) => getInstance[nestedT](map)
                  case Some(other) =>
                    throw new RuntimeException("Unexpected type for field " + other.getClass)
                  case None => $defaultValue.asInstanceOf[nestedT]
              }
            case None =>
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
          defaultValueOpt match
            case Some(defaultValue) =>
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
                  case None       => $defaultValue.asInstanceOf[nestedT]
                  case other =>
                    throw new RuntimeException("Unexpected value type for enum field '" + $keyToUse + "': " + other.getClass)
              }
            case None =>
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
          end match

        case '[nestedT] =>
          defaultValueOpt match
            case Some(defaultValue) =>
              '{
                $fieldData.get($keyToUse) match
                  case Some(rawValue) =>
                    try rawValue.asInstanceOf[nestedT]
                    catch
                      case ex: ClassCastException =>
                        throw new RuntimeException(
                          "Error casting field " + $paramNameExpr + ". Expected: " + $paramTypeShowExpr +
                            ", Actual: " + rawValue.getClass.getName,
                          ex
                        )
                  case None => $defaultValue.asInstanceOf[nestedT]
              }
            case None =>
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

  /** Finds the default value method for a given parameter index in a case class companion object.
    *
    * This method uses multiple strategies to find default parameter methods, making it more robust against potential changes in Scala
    * compiler conventions:
    *   1. First tries the standard naming pattern: `$lessinit$greater$default$<N>` 2. Falls back to searching all methods that match a
    *      default parameter pattern 3. Validates the method signature to ensure it's a no-arg method returning the expected type
    *
    * @param companionSymbol
    *   The companion object symbol of the case class.
    * @param companionRef
    *   A reference to the companion object.
    * @param paramIndex
    *   The 0-based index of the parameter in the case class constructor.
    * @return
    *   An optional expression representing the default value, if found.
    */
  private def findDefaultValueMethod(using q: Quotes)(
      companionSymbol: q.reflect.Symbol,
      companionRef: q.reflect.Term,
      paramIndex: Int
  ): Option[Expr[Any]] =
    import q.reflect.*

    // Strategy 1: Try the standard naming pattern (Scala 3.x convention)
    val standardMethodName = s"$$lessinit$$greater$$default$$${paramIndex + 1}"
    val standardMethod = companionSymbol.methodMember(standardMethodName).headOption

    if standardMethod.isDefined then return standardMethod.map(method => Select(companionRef, method).asExpr)

    // Strategy 2: Search for methods matching default parameter patterns
    // This is a fallback in case the naming convention changes
    val allMethods = companionSymbol.declaredMethods
    val defaultPattern = """.*default.*(\d+)""".r

    val fallbackMethod = allMethods.find { method =>
      method.name match
        case defaultPattern(indexStr) =>
          // Check if the index matches (1-based in method name)
          indexStr.toIntOption.contains(paramIndex + 1) &&
          // Verify it's a no-arg method (default value methods have no parameters)
          method.paramSymss.flatten.isEmpty
        case _ => false
    }

    fallbackMethod.map(method => Select(companionRef, method).asExpr)
  end findDefaultValueMethod
end CaseClassFactory
