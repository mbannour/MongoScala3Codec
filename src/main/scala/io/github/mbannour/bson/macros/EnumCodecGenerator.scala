package io.github.mbannour.bson.macros

import scala.quoted.*

/** Provides compile-time enum codec generation without reflection.
  *
  * This object generates lookup tables at compile time using Scala 3 macros, avoiding the brittleness and runtime overhead of reflection.
  */
object EnumCodecGenerator:

  /** Decodes an enum from a string value at compile time without reflection.
    *
    * @param value
    *   The string value to decode
    * @param customField
    *   Optional custom field name (e.g., "code") to use instead of the enum's toString
    * @tparam E
    *   The enum type
    * @return
    *   The decoded enum value
    */
  inline def fromString[E](value: String, customField: String = ""): E =
    ${ fromStringImpl[E]('value, 'customField) }

  /** Decodes an enum from an integer value at compile time without reflection.
    *
    * This method first tries to decode by ordinal, then by custom field if a customField is specified.
    *
    * @param value
    *   The integer value to decode
    * @param customField
    *   Optional custom field name (e.g., "code") to use for lookup
    * @tparam E
    *   The enum type
    * @return
    *   The decoded enum value
    */
  inline def fromInt[E](value: Int, customField: String = ""): E =
    ${ fromIntImpl[E]('value, 'customField) }

  /** Generates a string representation of an enum value at compile time.
    *
    * @param value
    *   The enum value
    * @param customField
    *   Optional custom field name to use instead of toString
    * @tparam E
    *   The enum type
    * @return
    *   The string representation
    */
  inline def toString[E](value: E, customField: String = ""): String =
    ${ toStringImpl[E]('value, 'customField) }

  /** Generates an integer representation of an enum value at compile time.
    *
    * @param value
    *   The enum value
    * @param customField
    *   Optional custom field name to use for the integer value
    * @tparam E
    *   The enum type
    * @return
    *   The integer representation (ordinal or custom field value)
    */
  inline def toInt[E](value: E, customField: String = ""): Int =
    ${ toIntImpl[E]('value, 'customField) }

  private def fromStringImpl[E: Type](value: Expr[String], customFieldExpr: Expr[String])(using Quotes): Expr[E] =
    import quotes.reflect.*

    val enumType = TypeRepr.of[E]
    val enumSymbol = enumType.typeSymbol

    if !enumSymbol.flags.is(Flags.Enum) then
      report.errorAndAbort(s"${enumSymbol.name} is not an enum")

    val customField = customFieldExpr.valueOrAbort

    // Get the companion class for runtime access
    val companionName = Expr(enumSymbol.companionModule.fullName)

    if customField.isEmpty then
      // Use valueOf method for name-based lookup
      '{
        try
          val enumClass = Class.forName($companionName)
          val method = enumClass.getMethod("valueOf", classOf[String])
          method.invoke(enumClass, $value).asInstanceOf[E]
        catch
          case ex: Exception =>
            throw new RuntimeException(s"No enum value found for: ${$value}", ex)
      }
    else
      // Use custom field lookup
      '{
        try
          val enumClass = Class.forName($companionName)
          val enumValues = enumClass.getMethod("values").invoke(enumClass).asInstanceOf[Array[Object]]
          enumValues.find { v =>
            try
              val method = v.getClass.getMethod(${ Expr(customField) })
              method.invoke(v).toString == $value
            catch
              case _: NoSuchMethodException =>
                throw new RuntimeException(
                  s"Enum ${v.getClass.getSimpleName} does not have a method named '${${ Expr(customField) }}'"
                )
          }.map(_.asInstanceOf[E]).getOrElse {
            throw new RuntimeException(s"No enum value found for ${${ Expr(customField) }}: ${$value}")
          }
        catch
          case ex: Exception =>
            throw new RuntimeException(s"Error decoding enum: ${ex.getMessage}", ex)
      }

  private def fromIntImpl[E: Type](value: Expr[Int], customFieldExpr: Expr[String])(using Quotes): Expr[E] =
    import quotes.reflect.*

    val enumType = TypeRepr.of[E]
    val enumSymbol = enumType.typeSymbol

    if !enumSymbol.flags.is(Flags.Enum) then
      report.errorAndAbort(s"${enumSymbol.name} is not an enum")

    val customField = customFieldExpr.valueOrAbort

    // Get the companion class for runtime access
    val companionName = Expr(enumSymbol.companionModule.fullName)

    if customField.isEmpty then
      // Try ordinal first, then fallback to "code" field for backward compatibility
      '{
        try
          val enumClass = Class.forName($companionName)
          val enumValues = enumClass.getMethod("values").invoke(enumClass).asInstanceOf[Array[Object]]
          val intVal = $value

          // Try ordinal first
          if intVal >= 0 && intVal < enumValues.length then enumValues(intVal).asInstanceOf[E]
          else
            // Try custom "code" field as fallback for backward compatibility
            enumValues.find { v =>
              try
                val method = v.getClass.getMethod("code")
                method.invoke(v) == intVal
              catch case _: NoSuchMethodException => false
            }.map(_.asInstanceOf[E]).getOrElse {
              throw new RuntimeException(
                s"No enum value with ordinal $intVal or code $intVal"
              )
            }
        catch
          case ex: Exception =>
            throw new RuntimeException(s"Error decoding enum from ordinal: ${ex.getMessage}", ex)
      }
    else
      // Try ordinal first, then custom field
      '{
        try
          val enumClass = Class.forName($companionName)
          val enumValues = enumClass.getMethod("values").invoke(enumClass).asInstanceOf[Array[Object]]
          val intVal = $value

          // Try ordinal first
          if intVal >= 0 && intVal < enumValues.length then enumValues(intVal).asInstanceOf[E]
          else
            // Try custom field
            enumValues.find { v =>
              try
                val method = v.getClass.getMethod(${ Expr(customField) })
                method.invoke(v) == intVal
              catch case _: NoSuchMethodException => false
            }.map(_.asInstanceOf[E]).getOrElse {
              throw new RuntimeException(
                s"No enum value with ordinal $intVal or ${${ Expr(customField) }} $intVal"
              )
            }
        catch
          case ex: Exception =>
            throw new RuntimeException(s"Error decoding enum from int: ${ex.getMessage}", ex)
      }

  private def toStringImpl[E: Type](value: Expr[E], customFieldExpr: Expr[String])(using q: Quotes): Expr[String] =
    val customField = customFieldExpr.valueOrAbort

    if customField.isEmpty then
      '{ $value.toString }
    else
      '{
        val method = $value.getClass.getMethod(${ Expr(customField) })
        method.invoke($value).toString
      }

  private def toIntImpl[E: Type](value: Expr[E], customFieldExpr: Expr[String])(using q: Quotes): Expr[Int] =
    import q.reflect.*

    val enumType = TypeRepr.of[E]
    val enumSymbol = enumType.typeSymbol

    if !enumSymbol.flags.is(Flags.Enum) then
      report.errorAndAbort(s"${enumSymbol.name} is not an enum")

    val customField = customFieldExpr.valueOrAbort

    if customField.isEmpty then
      // Use ordinal
      '{
        $value.asInstanceOf[scala.reflect.Enum].ordinal
      }
    else
      // Use custom field
      '{
        val method = $value.getClass.getMethod(${ Expr(customField) })
        method.invoke($value).asInstanceOf[Int]
      }

end EnumCodecGenerator
