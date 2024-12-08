package io.github.mbannour.bson.macros

import scala.quoted.*
import scala.reflect.ClassTag

object CaseClassFieldMapper:

  private[mbannour] inline def createClassFieldTypeArgsMap[T]: Map[String, Map[String, List[Class[?]]]] = ${
    createClassFieldTypeArgsMapImpl[T]
  }

  private[mbannour] def createClassFieldTypeArgsMapImpl[T: Type](using q: Quotes): Expr[Map[String, Map[String, List[Class[?]]]]] =

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

    def flattenTypeArgs(tpe: TypeRepr): List[TypeRepr] =
      val t = tpe.dealias
      val typeArgs = t match
        case AppliedType(_, args) if isMap(t) && !(args.head =:= TypeRepr.of[String]) =>
          report.errorAndAbort("Maps must contain string types for keys")
        case AppliedType(_, _ :: tail) if isMap(t) => tail
        case AppliedType(_, args)                  => args
        case _                                     => List.empty

      val types = t +: typeArgs.flatMap(flattenTypeArgs)
      if types.exists(isTuple) then report.errorAndAbort("Tuples currently aren't supported in case classes")
      types.filterNot(isOption).map(x => primitiveTypesMap.getOrElse(x, x)) // Convert primitives to boxed types
    end flattenTypeArgs

    def isOption(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Option[?]]

    def isMap(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Map[?, ?]]

    def isTuple(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Tuple]

    def isCaseClass(tpe: TypeRepr): Boolean = tpe.typeSymbol.isClassDef && tpe.typeSymbol.flags.is(Flags.Case)

    def getFieldNamesAndTypesRecursive(tpe: TypeRepr, visited: Set[TypeRepr]): List[Expr[(String, Map[String, List[Class[?]]])]] =
      if visited.contains(tpe) then return List.empty

      val fields = getFieldNamesAndTypes(tpe)
      val fieldTypeMap = createFieldTypeArgsMap(fields, visited + tpe)

      val classNameExpr = Expr(tpe.typeSymbol.name)

      val nestedClassMaps = fields.flatMap { case (_, fieldType) =>
        if isCaseClass(fieldType) then getFieldNamesAndTypesRecursive(fieldType, visited + tpe)
        else List.empty
      }

      '{ ($classNameExpr, $fieldTypeMap) } :: nestedClassMaps
    end getFieldNamesAndTypesRecursive

    def getFieldNamesAndTypes(tpe: TypeRepr): List[(String, TypeRepr)] =
      tpe.typeSymbol.primaryConstructor.paramSymss.flatten.map { fieldSymbol =>
        val fieldName = fieldSymbol.name
        val fieldType = fieldSymbol.tree.asInstanceOf[ValDef].tpt.tpe
        (fieldName, fieldType)
      }

    def createFieldTypeArgsMap(fields: List[(String, TypeRepr)], visited: Set[TypeRepr]): Expr[Map[String, List[Class[?]]]] =
      val entries: List[Expr[(String, List[Class[?]])]] = fields.map { case (name, f) =>
        val keyExpr = AnnotationName.findAnnotationValue[T](Expr(name))

        val keyToUse: Expr[String] = keyExpr match
          case '{ Some($annotationValue: String) } => annotationValue
          case '{ None }                           => Expr(name)

        val classTypesExpr = flattenTypeArgs(f).map { t =>
          t.asType match
            case '[tType] =>
              Expr.summon[ClassTag[tType]] match
                case Some(ct) =>
                  '{ $ct.runtimeClass.asInstanceOf[Class[?]] }
                case None =>
                  report.errorAndAbort(s"Cannot find ClassTag for type: ${t.show}")
        }

        val classTypesFlattenedExpr = Varargs(classTypesExpr)
        '{ $keyToUse -> List(${ classTypesFlattenedExpr }*) }
      }

      val entriesVarargs = Varargs(entries)
      '{ Map[String, List[Class[?]]](${ entriesVarargs }*) }
    end createFieldTypeArgsMap

    val mainType = TypeRepr.of[T]
    val knownTypes = getFieldNamesAndTypesRecursive(mainType, Set.empty)

    val mapEntriesExpr = Varargs(knownTypes)
    '{ Map[String, Map[String, List[Class[?]]]](${ mapEntriesExpr }*) }
  end createClassFieldTypeArgsMapImpl
end CaseClassFieldMapper
