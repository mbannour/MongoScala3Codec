package io.github.mbannour.bson.macros

import scala.quoted.*
import scala.reflect.ClassTag

object ClassToCaseFlagMap:

  /** Macro that generates a map from runtime Class to a Boolean flag, indicating whether the type is a case class or sealed. The map is
    * built for type `T` and recursively includes all nested field types (with primitives replaced by their boxed types).
    */
  private[mbannour] inline def classToCaseClassMap[T]: Map[Class[?], Boolean] =
    ${ classToCaseClassMapImpl[T] }

  private[mbannour] def classToCaseClassMapImpl[T: Type](using Quotes): Expr[Map[Class[?], Boolean]] =
    import quotes.reflect.*

    // Mapping from primitive types to their boxed counterparts.
    val primitiveToBoxedMap: Map[TypeRepr, TypeRepr] = Map(
      TypeRepr.of[Boolean] -> TypeRepr.of[java.lang.Boolean],
      TypeRepr.of[Byte] -> TypeRepr.of[java.lang.Byte],
      TypeRepr.of[Char] -> TypeRepr.of[java.lang.Character],
      TypeRepr.of[Double] -> TypeRepr.of[java.lang.Double],
      TypeRepr.of[Float] -> TypeRepr.of[java.lang.Float],
      TypeRepr.of[Int] -> TypeRepr.of[java.lang.Integer],
      TypeRepr.of[Long] -> TypeRepr.of[java.lang.Long],
      TypeRepr.of[Short] -> TypeRepr.of[java.lang.Short]
    )

    /** Returns true if the given type represents a case class or a sealed trait/class.
      */
    def isCaseOrSealed(tpe: TypeRepr): Boolean =
      tpe.typeSymbol.isClassDef &&
        (tpe.typeSymbol.flags.is(Flags.Case) || tpe.typeSymbol.flags.is(Flags.Sealed))

    /** Rejects a field whose type cannot be derived however the registry is configured.
      *
      * Only shapes that no codec could rescue are checked here. A field type that merely has no codec yet is left alone: the registry is a
      * runtime value, so a user or driver codec can still supply one, and rejecting it at compile time would turn a missing codec into a
      * permanent verdict. Without this, such a field reached the ClassTag lookup below and failed there, naming the field where a type
      * belongs and mentioning neither the model nor the type.
      */
    def validateFieldType(owner: Symbol, field: Symbol): Unit =
      val declaredType = field.tree match
        case vd: ValDef => Some(vd.tpt.tpe)
        case _          => None

      declaredType.foreach { fieldType =>
        val shownType = fieldType.show(using Printer.TypeReprShortCode)
        val location = s"field '${field.name}' has unsupported type '$shownType'"
        val preamble = s"MongoScala3Codec cannot derive a codec for '${owner.name}': $location"

        if fieldType <:< TypeRepr.of[Tuple] then
          report.errorAndAbort(
            s"$preamble." +
              "\n\nA BSON document names its values, and a tuple's elements have no names to write them under." +
              "\n\nSuggestions:" +
              s"\n  • Replace the tuple with a case class, whose parameter names become the BSON field names" +
              s"\n  • For a pair keyed by a string, Map[String, V] is encoded as an embedded document"
          )
        end if

        fieldType.asType match
          case '[f] =>
            if Expr.summon[ClassTag[f]].isEmpty then
              report.errorAndAbort(
                s"$preamble." +
                  "\n\nEncoding needs the field's runtime class, and this type does not have one, so it is not a concrete" +
                  " type that can be written to a document. A type parameter of a generic model is the usual cause: derivation happens" +
                  " per concrete model, and the parameter is still abstract at that point." +
                  "\n\nSuggestions:" +
                  s"\n  • Give '${field.name}' a concrete type" +
                  s"\n  • Replace the generic model with a concrete one per field type it is used at"
              )
        end match
      }
    end validateFieldType

    /** Recursively collects field types from the primary constructor of the given type. If a field type is a case class or sealed, its
      * fields are also collected.
      */
    def collectFieldTypes(tpe: TypeRepr): List[TypeRepr] =
      val paramTypes = tpe.typeSymbol.primaryConstructor.paramSymss.flatten.collect {
        case sym if sym.isTerm && sym.isValDef =>
          validateFieldType(tpe.typeSymbol, sym)
          sym.termRef.asType match
            case '[f] => TypeRepr.of[f]
      }
      paramTypes.flatMap { fieldTpe =>
        if isCaseOrSealed(fieldTpe) then collectFieldTypes(fieldTpe) :+ fieldTpe
        else List(fieldTpe)
      }
    end collectFieldTypes

    /** Recursively flattens a type to include itself and all of its type arguments. Additionally, replaces primitive types with their boxed
      * counterparts.
      */
    def flattenTypeArguments(tpe: TypeRepr): List[TypeRepr] =
      val typeArgs = tpe match
        case AppliedType(_, args) => args
        case _                    => Nil
      val allTypes = tpe :: typeArgs.flatMap(flattenTypeArguments)
      allTypes.map(t => primitiveToBoxedMap.getOrElse(t, t))

    /** Returns the complete list of field types for the given type, including the type itself.
      */
    def getAllFieldTypes(tpe: TypeRepr): List[TypeRepr] =
      collectFieldTypes(tpe) :+ tpe

    // Compute the flattened and distinct list of all field types for T.
    val mainType = TypeRepr.of[T]
    val flattenedFieldTypes: List[TypeRepr] =
      getAllFieldTypes(mainType).flatMap(flattenTypeArguments)
    val distinctFieldTypes = flattenedFieldTypes.distinct

    // Build an expression mapping from each type's runtime class to a Boolean flag (true if it is case/sealed).
    val classToCaseClassEntries: List[Expr[(Class[?], Boolean)]] = distinctFieldTypes.map { tpe =>
      tpe.asType match
        case '[t] =>
          Expr.summon[ClassTag[t]] match
            case Some(classTagExpr) =>
              val classExpr = '{ $classTagExpr.runtimeClass.asInstanceOf[Class[?]] }
              val isCaseExpr = Expr(isCaseOrSealed(tpe))
              '{ $classExpr -> $isCaseExpr }
            case None =>
              report.errorAndAbort(s"Cannot summon ClassTag for type: ${tpe.show}")
    }

    val mapEntriesExpr = Varargs(classToCaseClassEntries)
    '{ Map[Class[?], Boolean]($mapEntriesExpr*) }
  end classToCaseClassMapImpl
end ClassToCaseFlagMap
