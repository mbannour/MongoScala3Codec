package io.github.mbannour.bson.macros

import scala.quoted.*
import scala.reflect.ClassTag

/** Everything known at compile time about one concrete subtype of a sealed hierarchy, from which a discriminator value can be derived at
  * runtime.
  *
  * The three name sources are carried side by side rather than collapsed into one string, because the choice between them is
  * `CodecConfig.discriminatorStrategy` - a runtime value. A macro cannot read it, so it hands the alternatives over instead and lets
  * [[io.github.mbannour.mongo.codecs.DiscriminatorResolver]] pick.
  *
  * @param simpleName
  *   the subtype's simple name, with compiler-generated artifacts stripped
  * @param fullName
  *   the subtype's fully qualified name, normalised so a nested subtype reads as `pkg.Outer.Inner` rather than `pkg.Outer$.Inner`
  * @param annotated
  *   the `@BsonDiscriminator` value when the subtype carries one; it outranks any strategy
  * @param runtimeClass
  *   the subtype's runtime class, which is also the key `DiscriminatorStrategy.Custom` maps from
  */
private[mbannour] final case class SubtypeDiscriminator(
    simpleName: String,
    fullName: String,
    annotated: Option[String],
    runtimeClass: Class[?]
)

/** Provides macros for mapping a discriminator to the runtime Class of a case class. This is especially useful for sealed hierarchies,
  * where `@BsonDiscriminator` may override the default name.
  */
object CaseClassMapper:

  /** Macro that generates a Map[String, Class[?]] for type `T` and its case class subclasses.
    *
    * The keys in the map are simple class names (optionally overridden via an annotation), and the values are the corresponding runtime
    * classes.
    */
  private[mbannour] inline def caseClassMap[T]: Map[String, Class[?]] =
    ${ caseClassMapImpl[T] }

  /** Macro that generates the per-subtype discriminator metadata for type `T` and its case class subclasses.
    *
    * Same discovery and same compile-time validation as [[caseClassMap]], but it stops short of choosing a discriminator value so that a
    * `DiscriminatorStrategy` can be applied at runtime.
    */
  private[mbannour] inline def caseClassSubtypes[T]: List[SubtypeDiscriminator] =
    ${ caseClassSubtypesImpl[T] }

  /** Simplifies a fully-qualified class name by extracting the simple name and removing compiler-generated artifacts.
    */
  private def simpleClassName(fullName: String): String =
    fullName
      .split('.')
      .lastOption
      .getOrElse(fullName)
      .replaceAll("^_+", "")
      .replaceAll("\\$\\d+", "")
      .replaceAll("\\$+", "")

  /** Normalises a symbol's fully qualified name for use as a discriminator.
    *
    * `Symbol.fullName` spells the enclosing object of a nested subtype with a `$`, which would put a synthetic-looking `pkg.Outer$.Inner`
    * into stored documents. The separator is folded back to a dot so the stored value reads the way the source does.
    */
  private def normalizedFullName(fullName: String): String =
    fullName
      .replace("$.", ".")
      .replaceAll("\\$+$", "")
      .replaceAll("\\$\\d+", "")

  /** Discovers the concrete case class subtypes of `T`, aborting with a diagnostic when there are none.
    *
    * Shared by both macros so that the two can never disagree about which subtypes exist or which of them are rejected.
    */
  private def validatedCaseClassSymbols[T: Type](using q: Quotes): (q.reflect.Symbol, List[q.reflect.Symbol]) =
    import q.reflect.*

    val mainType = TypeRepr.of[T]
    val mainSymbol = mainType.typeSymbol

    /** Returns true if the given symbol is sealed. */
    def isSealed(symbol: Symbol): Boolean =
      symbol.isClassDef && symbol.flags.is(Flags.Sealed)

    /** Returns true if the given symbol represents a case class. */
    def isCaseClass(symbol: Symbol): Boolean =
      symbol.isClassDef && symbol.flags.is(Flags.Case)

    /** Recursively collects all subclasses of a sealed symbol. */
    def subclasses(symbol: Symbol): Set[Symbol] =
      val directSubclasses = symbol.children.toSet
      directSubclasses ++ directSubclasses.flatMap(subclasses)

    // Determine all candidate symbols: if the main symbol is sealed, include its subclasses; otherwise, use the main symbol.
    val knownTypes: Set[Symbol] =
      if isSealed(mainSymbol) then subclasses(mainSymbol) + mainSymbol
      else Set(mainSymbol)

    // Filter only those symbols that are case classes.
    val caseClassSymbols: Set[Symbol] = knownTypes.filter(isCaseClass)

    if caseClassSymbols.isEmpty then
      if isSealed(mainSymbol) then
        val kind = if mainSymbol.flags.is(Flags.Trait) then "trait" else "class"
        val typeName = mainSymbol.name
        val allSubclasses = subclasses(mainSymbol)
        val subclassInfo = if allSubclasses.nonEmpty then
          val names = allSubclasses.map(_.name).mkString(", ")
          s"\n\nFound subclasses: $names (but none are case classes)"
        else "\n\nNo subclasses found at all."

        report.errorAndAbort(
          s"MongoScala3Codec cannot derive a codec for the sealed $kind '$typeName': it has no case class subtypes." +
            subclassInfo +
            "\n\nSuggestions:" +
            s"\n  • Ensure all subclasses of '$typeName' are case classes" +
            s"\n  • Example:\n" +
            s"      sealed trait $typeName\n" +
            s"      case class SubType1(...) extends $typeName\n" +
            s"      case class SubType2(...) extends $typeName"
        )
      else
        val typeName = mainSymbol.name

        report.errorAndAbort(
          s"MongoScala3Codec cannot derive a codec for '$typeName': it is neither a case class nor a sealed trait/class." +
            "\n\nSuggestion:" +
            s"\n  • Make '$typeName' a case class:\n" +
            s"      case class $typeName(...)" +
            s"\n  • Or, if '$typeName' represents a type hierarchy, make it sealed with case class subtypes:\n" +
            s"      sealed trait $typeName\n" +
            s"      case class SubType1(...) extends $typeName"
        )
      end if
    end if

    (mainSymbol, caseClassSymbols.toList)
  end validatedCaseClassSymbols

  /** The explicit `@BsonDiscriminator` value on a subtype, if it carries one.
    *
    * Rejects an empty value here, where the subtype being annotated is still nameable.
    */
  private def annotatedDiscriminator(using q: Quotes)(symbol: q.reflect.Symbol): Option[String] =
    import q.reflect.*

    val bsonDiscriminatorSymbol = TypeRepr.of[BsonDiscriminator].typeSymbol

    symbol.getAnnotation(bsonDiscriminatorSymbol) match
      case Some(Apply(_, List(Literal(StringConstant(value))))) =>
        // An empty value reads as a missing discriminator in a stored document, and is never what the
        // annotation was reached for. Only an explicit value can be empty; a simple name cannot.
        if value.isEmpty then
          report.errorAndAbort(
            s"MongoScala3Codec cannot derive a codec for '${symbol.name}': its @BsonDiscriminator value is empty." +
              "\n\nThe discriminator is what tells a stored document which subtype it holds, and an empty one is" +
              " indistinguishable from no discriminator at all, so empty values are unsupported." +
              "\n\nSuggestions:" +
              s"\n  • Give '${symbol.name}' a non-empty, stable value: @BsonDiscriminator(\"...\")" +
              s"\n  • Or drop the annotation to record '${symbol.name}' under its own name"
          )
        end if
        Some(value)
      case Some(other) =>
        report.errorAndAbort(s"Unexpected @BsonDiscriminator annotation on '${symbol.name}': ${other.show}")
      case None => None
    end match
  end annotatedDiscriminator

  /** Rejects two subtypes that would be stored under the same discriminator.
    *
    * Checked against the default (annotation-or-simple-name) values, which is what the map built here is keyed by. A
    * `DiscriminatorStrategy` chosen at runtime is re-checked then, by `DiscriminatorResolver`, because the strategy is not knowable now.
    */
  private def rejectDuplicateDiscriminators(using
      q: Quotes
  )(mainSymbol: q.reflect.Symbol, caseClassSymbols: List[q.reflect.Symbol], discriminatorOf: q.reflect.Symbol => String): Unit =
    import q.reflect.*

    // The subtypes are keyed by discriminator below, so two sharing a value would collapse into one
    // map entry: one subtype would silently disappear, and a document holding it would decode as the
    // other. Rejected here, where every concrete subtype of this hierarchy and its effective value are
    // known. Values compare as exact strings, so 'dog' and 'Dog' are distinct.
    val subtypesByDiscriminator: List[(String, List[Symbol])] =
      caseClassSymbols
        .sortBy(_.name)
        .groupBy(discriminatorOf)
        .toList
        .sortBy(_._1)

    // Sorted above, so the collision reported is deterministic when a hierarchy has more than one.
    subtypesByDiscriminator.find(_._2.sizeIs > 1).foreach { (discriminator, symbols) =>
      // Two subtypes can share a simple name through different objects, and then simple names would name
      // neither of them; fall back to the full name for the whole group so the two read differently.
      val simpleNamesAreDistinct = symbols.map(_.name).distinct.sizeIs == symbols.size
      val names =
        symbols.map(symbol => if simpleNamesAreDistinct then s"'${symbol.name}'" else s"'${symbol.fullName}'").mkString(", ")

      report.errorAndAbort(
        s"MongoScala3Codec cannot derive a codec for '${mainSymbol.name}': duplicate discriminator value '$discriminator'." +
          s"\n\nIt is the effective discriminator of ${symbols.size} subtypes: $names. A document records only the" +
          " discriminator, so decoding could not tell these subtypes apart." +
          "\n\nA subtype's effective discriminator is its @BsonDiscriminator value, or its simple name when it is not" +
          " annotated, so a custom value can collide with another subtype's name." +
          "\n\nSuggestions:" +
          s"\n  • Give one of $names a distinct @BsonDiscriminator(\"...\") value" +
          "\n  • Discriminator values are compared exactly, so they may differ only in case"
      )
    }
  end rejectDuplicateDiscriminators

  private[mbannour] def caseClassMapImpl[T: Type](using Quotes): Expr[Map[String, Class[?]]] =
    import quotes.reflect.*

    val (mainSymbol, caseClassSymbols) = validatedCaseClassSymbols[T]

    /** The discriminator value for a concrete subtype: the `@BsonDiscriminator` value when annotated, its simple name otherwise.
      *
      * This is the only place the choice is made. Encoding reads the inverse of the map built here and decoding reads the map itself, so
      * both directions necessarily agree on one value per subtype.
      */
    def effectiveDiscriminator(symbol: Symbol): String =
      annotatedDiscriminator(symbol).getOrElse(simpleClassName(symbol.fullName))

    rejectDuplicateDiscriminators(mainSymbol, caseClassSymbols, effectiveDiscriminator)

    // Build an expression for each case class entry: (discriminator, runtimeClass)
    val caseClassEntries: List[Expr[(String, Class[?])]] =
      caseClassSymbols.collect {
        case symbol if symbol.typeRef.classSymbol.isDefined =>
          val nameExpr: Expr[String] = Expr(effectiveDiscriminator(symbol))

          symbol.typeRef.asType match
            case '[tType] =>
              Expr.summon[ClassTag[tType]] match
                case Some(classTagExpr) =>
                  '{ $nameExpr -> $classTagExpr.runtimeClass }
                case None =>
                  report.errorAndAbort(s"Cannot summon ClassTag for ${symbol.fullName}.")
      }

    '{ Map[String, Class[?]](${ Varargs(caseClassEntries) }*) }
  end caseClassMapImpl

  private[mbannour] def caseClassSubtypesImpl[T: Type](using Quotes): Expr[List[SubtypeDiscriminator]] =
    import quotes.reflect.*

    val (mainSymbol, caseClassSymbols) = validatedCaseClassSymbols[T]

    // The same rejection the default map performs, kept here so that switching a codec over to the
    // strategy-aware path cannot lose a compile error the hierarchy used to get.
    def defaultDiscriminator(symbol: Symbol): String =
      annotatedDiscriminator(symbol).getOrElse(simpleClassName(symbol.fullName))

    rejectDuplicateDiscriminators(mainSymbol, caseClassSymbols, defaultDiscriminator)

    val entries: List[Expr[SubtypeDiscriminator]] =
      caseClassSymbols.collect {
        case symbol if symbol.typeRef.classSymbol.isDefined =>
          val simpleNameExpr: Expr[String] = Expr(simpleClassName(symbol.fullName))
          val fullNameExpr: Expr[String] = Expr(normalizedFullName(symbol.fullName))
          val annotatedExpr: Expr[Option[String]] = annotatedDiscriminator(symbol) match
            case Some(value) => '{ Some(${ Expr(value) }) }
            case None        => '{ None }

          symbol.typeRef.asType match
            case '[tType] =>
              Expr.summon[ClassTag[tType]] match
                case Some(classTagExpr) =>
                  '{ SubtypeDiscriminator($simpleNameExpr, $fullNameExpr, $annotatedExpr, $classTagExpr.runtimeClass) }
                case None =>
                  report.errorAndAbort(s"Cannot summon ClassTag for ${symbol.fullName}.")
      }

    '{ List[SubtypeDiscriminator](${ Varargs(entries) }*) }
  end caseClassSubtypesImpl
end CaseClassMapper
