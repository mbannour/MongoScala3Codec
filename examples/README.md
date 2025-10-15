# Examples Directory

This directory contains example code demonstrating **ideal APIs** for features that are **not yet implemented** in MongoScala3Codec.

## Purpose

These examples serve as:
1. **Specification** - What the API should look like
2. **Documentation** - How the features should work
3. **Reference** - For contributors implementing these features
4. **Discussion** - Starting point for API design conversations

## ⚠️ Important Note

**These examples will NOT compile** because the features are not implemented yet. They represent the target API design.

## Examples

### 1. PolymorphicSealedTraitExample.scala
**Status:** ❌ Not Implemented  
**Priority:** 🔴 HIGH  
**Description:** Shows how polymorphic sealed trait fields should work

```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

// This should work:
case class User(_id: ObjectId, name: String, status: Status)
```

**Key Features:**
- Polymorphic fields typed as sealed traits
- Collections of sealed trait values
- Case object support
- Configurable discriminator strategies
- Nested sealed traits

### 2. CustomFieldTransformationExample.scala
**Status:** ❌ Not Implemented  
**Priority:** 🟡 MEDIUM  
**Description:** Shows how field name transformations should work

```scala
enum FieldNamingStrategy:
  case SnakeCase      // firstName -> first_name
  case CamelCase      // first_name -> firstName
  case PascalCase     // firstName -> FirstName
  case Custom(f: String => String)

given config: CodecConfig = CodecConfig(
  fieldNamingStrategy = FieldNamingStrategy.SnakeCase
)
```

**Key Features:**
- Built-in naming strategies (snake_case, camelCase, etc.)
- Custom transformation functions
- Override with @BsonProperty annotation
- Global configuration

### 3. EnhancedEnumSupportExample.scala
**Status:** ⚠️ Partially Implemented  
**Priority:** 🟡 MEDIUM  
**Description:** Shows how parameterized Scala 3 enums should work

```scala
// Currently supported:
enum Priority:
  case Low, Medium, High

// Should be supported:
enum Result:
  case Success(value: String)
  case Failure(error: String, code: Int)
```

**Key Features:**
- Parameterized enum cases (ADT style)
- Enums with custom fields
- Enums in collections
- Configurable encoding strategies

## How to Use These Examples

### For Library Users
- Review these examples to see what's coming
- Open issues to discuss the proposed APIs
- Vote on features you'd like to see

### For Contributors
1. Pick an example that interests you
2. Read the implementation notes at the bottom
3. Open an issue to discuss your approach
4. Implement the feature with tests
5. Update ROADMAP.md when complete

### For Maintainers
- Use these as specification documents
- Reference during API design discussions
- Update when APIs change
- Move to main codebase when implemented

## Testing These Examples

Since these features aren't implemented, the examples won't compile. However, you can:

1. **Read the code** to understand the intended behavior
2. **Write tests** for the expected behavior (they'll fail until implemented)
3. **Create issues** to track implementation progress

## Implementation Order

Based on [ROADMAP.md](../ROADMAP.md), the recommended implementation order is:

1. **PolymorphicSealedTraitExample** (HIGH priority)
   - Core ADT support
   - Enables true domain modeling

2. **CustomFieldTransformationExample** (MEDIUM priority)
   - Quality of life improvement
   - Reduces boilerplate

3. **EnhancedEnumSupportExample** (MEDIUM priority)
   - Builds on sealed trait support
   - Completes Scala 3 integration

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for detailed contribution guidelines.

Key points:
- Open an issue before starting work
- Reference these examples in your PR
- Add tests that validate the examples work
- Update documentation when features are complete

## Questions?

- Open an [issue](https://github.com/mbannour/MongoScala3Codec/issues)
- Reference the relevant example file
- Ask about API design or implementation approach

---

*These examples represent the vision for making MongoScala3Codec an excellent library with full Scala 3 support.*
