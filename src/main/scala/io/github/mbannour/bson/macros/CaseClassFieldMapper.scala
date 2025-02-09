package io.github.mbannour.bson.macros

import scala.quoted.*
import scala.reflect.ClassTag

/** Provides a macro to generate a mapping for case class field type arguments.
  *
  * The macro produces a Map where:
  *   - The outer keys are the names (as determined by the case class’s type symbol) of the case classes.
  *   - The inner maps have keys representing field names (which may be overridden via annotations) and values as lists of runtime Class
  *     objects for the flattened type arguments of that field.
  */
object CaseClassFieldMapper:

  /** Macro that generates a map from case class names to their fields’ type argument mappings.
    *
    * @tparam T
    *   the root case class type to inspect recursively.
    * @return
    *   a Map[String, Map[String, List[Class[?]]]] representing the field type arguments.
    */
  private[mbannour] inline def createClassFieldTypeArgsMap[T]: Map[String, Map[String, List[Class[?]]]] =
    ${ createClassFieldTypeArgsMapImpl[T] }

  private[mbannour] def createClassFieldTypeArgsMapImpl[T: Type](using Quotes): Expr[Map[String, Map[String, List[Class[?]]]]] =
    import quotes.reflect.*

    val primitiveTypesMap: Map[TypeRepr, TypeRepr] = Map(
      TypeRepr.of[Boolean] -> TypeRepr.of[java.lang.Boolean],
      TypeRepr.of[Byte] -> TypeRepr.of[java.lang.Byte],
      TypeRepr.of[Char] -> TypeRepr.of[java.lang.Character],
      TypeRepr.of[Double] -> TypeRepr.of[java.lang.Double],
      TypeRepr.of[Float] -> TypeRepr.of[java.lang.Float],
      TypeRepr.of[Int] -> TypeRepr.of[java.lang.Integer],
      TypeRepr.of[Long] -> TypeRepr.of[java.lang.Long],
      TypeRepr.of[Short] -> TypeRepr.of[java.lang.Short]
    )

    /** Recursively flattens the type arguments of the given type.
      *
      *   - For Map types, ensures that the key type is String.
      *   - Recursively includes the type and its type arguments.
      *   - Filters out Option types.
      *   - Converts primitive types to their boxed versions.
      */
    def flattenTypeArgs(tpe: TypeRepr): List[TypeRepr] =
      val dealiased = tpe.dealias
      val typeArgs = dealiased match
        case AppliedType(_, args) if isMap(dealiased) && !(args.head =:= TypeRepr.of[String]) =>
          report.errorAndAbort("Maps must contain string types for keys")
        case AppliedType(_, _ :: tail) if isMap(dealiased) => tail
        case AppliedType(_, args)                          => args
        case _                                             => List.empty

      val allTypes = dealiased :: typeArgs.flatMap(flattenTypeArgs)
      if allTypes.exists(isTuple) then report.errorAndAbort("Tuples currently aren't supported in case classes")
      allTypes.filterNot(isOption).map(t => primitiveTypesMap.getOrElse(t, t))
    end flattenTypeArgs

    /** Returns true if the given type is an Option. */
    def isOption(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Option[?]]

    /** Returns true if the given type is a Map. */
    def isMap(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Map[?, ?]]

    /** Returns true if the given type is a Tuple. */
    def isTuple(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Tuple]

    /** Returns true if the given type represents a case class. */
    def isCaseClass(tpe: TypeRepr): Boolean =
      tpe.typeSymbol.isClassDef && tpe.typeSymbol.flags.is(Flags.Case)

    /** Recursively collects field name and type argument mappings for the given type.
      *
      * @param tpe
      *   the type to inspect.
      * @param visited
      *   the set of types already processed (to avoid cycles).
      * @return
      *   a list of expressions representing (className, fieldTypeArgsMap) pairs.
      */
    def getFieldNamesAndTypesRecursive(tpe: TypeRepr, visited: Set[TypeRepr]): List[Expr[(String, Map[String, List[Class[?]]])]] =
      if visited.contains(tpe) then Nil
      else
        val fields = getFieldNamesAndTypes(tpe)
        val fieldTypeMapExpr = createFieldTypeArgsMap(fields, visited + tpe)
        val classNameExpr = Expr(tpe.typeSymbol.name)
        val nestedClassMaps = fields.flatMap { case (_, fieldType) =>
          if isCaseClass(fieldType) then getFieldNamesAndTypesRecursive(fieldType, visited + tpe)
          else Nil
        }

        '{ ($classNameExpr, $fieldTypeMapExpr) } :: nestedClassMaps
    end getFieldNamesAndTypesRecursive

    /** Extracts field names and their types from the primary constructor of the given type.
      *
      * @param tpe
      *   the type to inspect.
      * @return
      *   a list of (fieldName, fieldType) pairs.
      */
    def getFieldNamesAndTypes(tpe: TypeRepr): List[(String, TypeRepr)] =
      tpe.typeSymbol.primaryConstructor.paramSymss.flatten.map { fieldSymbol =>
        val fieldName = fieldSymbol.name
        val fieldType = fieldSymbol.tree match
          case vd: ValDef => vd.tpt.tpe
          case _          => report.errorAndAbort(s"Expected a ValDef for field ${fieldSymbol.name}")
        (fieldName, fieldType)
      }

    /** Creates a mapping from field names to a list of runtime Class objects representing the field's type arguments.
      *
      * The field name can be overridden via annotations.
      *
      * @param fields
      *   the list of (fieldName, fieldType) pairs.
      * @param visited
      *   the set of types already processed.
      * @return
      *   an Expr of Map[String, List[Class[?]]].
      */
    def createFieldTypeArgsMap(fields: List[(String, TypeRepr)], visited: Set[TypeRepr]): Expr[Map[String, List[Class[?]]]] =
      val entries: List[Expr[(String, List[Class[?]])]] = fields.map { case (name, fieldType) =>
        // Determine the effective field name via annotation (if provided), or use the default.
        val annotatedNameExpr = AnnotationName.findAnnotationValue[T](Expr(name)) match
          case '{ Some($annotName: String) } => annotName
          case '{ None }                     => Expr(name)
        // Get the flattened type arguments for the field.
        val classListExprs: List[Expr[Class[?]]] = flattenTypeArgs(fieldType).map { t =>
          t.asType match
            case '[tType] =>
              Expr.summon[ClassTag[tType]] match
                case Some(classTag) =>
                  '{ $classTag.runtimeClass.asInstanceOf[Class[?]] }
                case None =>
                  report.errorAndAbort(s"Cannot find ClassTag for type: ${t.show}")
        }
        val classListExpr = Varargs(classListExprs)
        '{ $annotatedNameExpr -> List(${ classListExpr }*) }
      }
      '{ Map[String, List[Class[?]]](${ Varargs(entries) }*) }
    end createFieldTypeArgsMap

    val mainType = TypeRepr.of[T]
    val classFieldMapsExpr =
      Varargs(getFieldNamesAndTypesRecursive(mainType, Set.empty))
    '{ Map[String, Map[String, List[Class[?]]]]($classFieldMapsExpr*) }
  end createClassFieldTypeArgsMapImpl
end CaseClassFieldMapper
