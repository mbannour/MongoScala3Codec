# Contributing to MongoScala3Codec

Thank you for your interest in contributing to MongoScala3Codec! This guide will help you get started.

## Table of Contents
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Priority Features](#priority-features)

---

## Getting Started

MongoScala3Codec is a macro-based library for generating BSON codecs for Scala 3 case classes. Before contributing:

1. Read the [README.md](README.md) to understand the library's features
2. Review [ROADMAP.md](ROADMAP.md) to see what features are planned
3. Check [IMPROVEMENTS.md](IMPROVEMENTS.md) to understand recent improvements
4. Look at existing [issues](https://github.com/mbannour/MongoScala3Codec/issues) and [pull requests](https://github.com/mbannour/MongoScala3Codec/pulls)

---

## Development Setup

### Prerequisites
- JDK 11 or higher
- sbt 1.9.9 or higher
- Scala 3.3.1 or higher
- Git

### Clone and Build
```bash
git clone https://github.com/mbannour/MongoScala3Codec.git
cd MongoScala3Codec

# Compile the project
sbt compile

# Run tests
sbt test

# Run integration tests (requires Docker for MongoDB testcontainers)
sbt integrationTests/test

# Run all tests
sbt "test; integrationTests/test"
```

### Project Structure
```
MongoScala3Codec/
├── src/main/scala/               # Main library code
│   ├── io/github/mbannour/
│   │   ├── bson/macros/          # BSON writing/reading macros
│   │   ├── fields/               # Field name resolution
│   │   └── mongo/codecs/         # Public API (RegistryBuilder, CodecConfig, etc.)
├── src/test/scala/               # Unit tests
├── integration/src/test/scala/   # Integration tests with real MongoDB
├── build.sbt                     # Build configuration
├── README.md                     # User documentation
├── IMPROVEMENTS.md               # Recent improvements
├── ROADMAP.md                    # Future enhancements
└── CONTRIBUTING.md               # This file
```

### Key Files to Understand

1. **RegistryBuilder.scala** - Fluent API for building codec registries
2. **CodecConfig.scala** - Type-safe configuration
3. **CaseClassCodecGenerator.scala** - Core codec generation macro
4. **CodecProviderMacro.scala** - CodecProvider generation
5. **CodecTestKit.scala** - Testing utilities

---

## How to Contribute

### 1. Find or Create an Issue
- Check existing [issues](https://github.com/mbannour/MongoScala3Codec/issues)
- For new features, reference [ROADMAP.md](ROADMAP.md)
- Open a new issue to discuss your proposal before starting work

### 2. Fork and Branch
```bash
# Fork the repository on GitHub, then:
git clone https://github.com/YOUR_USERNAME/MongoScala3Codec.git
cd MongoScala3Codec

# Create a feature branch
git checkout -b feature/your-feature-name
```

### 3. Make Your Changes
- Write clean, idiomatic Scala 3 code
- Follow existing code style
- Add tests for new functionality
- Update documentation (README.md, ScalaDoc)

### 4. Test Your Changes
```bash
# Run unit tests
sbt test

# Run integration tests (if applicable)
sbt integrationTests/test

# Check formatting
sbt scalafmtCheck

# Fix formatting
sbt scalafmt
```

### 5. Commit and Push
```bash
git add .
git commit -m "feat: add support for sealed trait fields"
git push origin feature/your-feature-name
```

### 6. Open a Pull Request
- Go to the original repository
- Click "New Pull Request"
- Describe your changes clearly
- Reference related issues

---

## Code Style

### Scala Style Guidelines
- Follow [Scala 3 Style Guide](https://docs.scala-lang.org/style/)
- Use 2 spaces for indentation
- Maximum line length: 120 characters
- Use `given` instances for type classes
- Prefer `enum` over sealed traits for simple ADTs

### Naming Conventions
```scala
// Types: PascalCase
case class UserProfile(...)
sealed trait PaymentStatus

// Values and methods: camelCase
def generateCodec[T](...): Codec[T]
val codecRegistry: CodecRegistry

// Constants: camelCase (not UPPER_CASE)
val defaultConfig = CodecConfig()

// Type parameters: single capital letter or PascalCase
def register[T](...)
def map[A, B](...)
```

### ScalaDoc Comments
Always document public APIs:
```scala
/** Creates a codec for the given case class type.
  *
  * This method generates a BSON codec at compile time using Scala 3 macros.
  * It supports nested case classes, collections, and optional fields.
  *
  * @tparam T the case class type to generate a codec for
  * @param config configuration for codec behavior
  * @param registry base codec registry for nested types
  * @return a Codec instance for type T
  * @example
  * {{{
  * case class User(name: String, age: Int)
  * val codec = generateCodec[User](config, registry)
  * }}}
  */
def generateCodec[T](config: CodecConfig, registry: CodecRegistry): Codec[T]
```

---

## Testing

### Unit Tests
Unit tests use ScalaTest and should be fast (no I/O):

```scala
// src/test/scala/.../YourFeatureSpec.scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class YourFeatureSpec extends AnyFlatSpec with Matchers:
  
  "YourFeature" should "handle basic case" in {
    // Arrange
    val input = ...
    
    // Act
    val result = yourFunction(input)
    
    // Assert
    result shouldBe expected
  }
  
  it should "handle edge cases" in {
    // Test edge cases
  }
```

### Integration Tests
Integration tests use MongoDB testcontainers:

```scala
// integration/src/test/scala/.../YourIntegrationSpec.scala
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.MongoDBContainer

class YourIntegrationSpec 
  extends AnyFlatSpec 
  with Matchers 
  with TestContainerForAll:
  
  override val containerDef = MongoDBContainer.Def()
  
  it should "work with real MongoDB" in withContainers { mongo =>
    // Test with actual MongoDB
  }
```

### Testing Best Practices
- ✅ Test both success and failure cases
- ✅ Test edge cases (empty collections, None values, etc.)
- ✅ Test compile-time errors (when applicable)
- ✅ Use property-based testing for complex logic
- ✅ Keep tests independent (no shared mutable state)
- ❌ Don't test private methods directly
- ❌ Don't skip tests without a good reason

### Using CodecTestKit
For codec testing, use the provided utilities:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

test("User codec should roundtrip correctly") {
  val user = User(new ObjectId(), "Alice", 30)
  CodecTestKit.assertCodecSymmetry(user)
}

test("User codec should produce expected BSON structure") {
  val user = User(new ObjectId(), "Alice", 30)
  val bson = CodecTestKit.toBsonDocument(user)
  
  bson.getString("name").getValue shouldBe "Alice"
  bson.getInt32("age").getValue shouldBe 30
}
```

---

## Pull Request Process

### Before Submitting
- [ ] All tests pass (`sbt "test; integrationTests/test"`)
- [ ] Code is formatted (`sbt scalafmt`)
- [ ] Documentation is updated
- [ ] New tests are added for new features
- [ ] Commit messages follow convention

### Commit Message Convention
```
<type>: <description>

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `perf`: Performance improvement
- `chore`: Build/tooling changes

Examples:
```
feat: add support for polymorphic sealed trait fields

Implements codec generation for sealed trait hierarchies with
runtime type discrimination using discriminator fields.

Closes #123
```

```
fix: handle case objects in sealed hierarchies

Case objects are now properly serialized as singleton values
instead of throwing runtime exceptions.

Fixes #456
```

### Review Process
1. Maintainers will review your PR
2. Address feedback and update your branch
3. Once approved, your PR will be merged
4. Your contribution will be credited in the release notes

---

## Priority Features

Want to make a big impact? Work on these high-priority features from [ROADMAP.md](ROADMAP.md):

### 🔴 High Priority (v1.0)
1. **Polymorphic Sealed Trait Fields**
   - Issue: #TBD
   - Complexity: High
   - Impact: Unlocks full ADT support

2. **Case Object Support**
   - Issue: #TBD
   - Complexity: Medium
   - Impact: Complete sealed trait support

### 🟡 Medium Priority (v1.1)
3. **Custom Field Name Transformations**
   - Issue: #TBD
   - Complexity: Medium
   - Impact: Reduces boilerplate

4. **Enhanced Enum Support**
   - Issue: #TBD
   - Complexity: Medium
   - Impact: Better Scala 3 integration

---

## Implementation Guidelines

### For Macro-Based Features
When working with Scala 3 macros:

1. **Understand the existing macro code**
   - Read `CaseClassCodecGenerator.scala`
   - Understand quote/splice syntax
   - Review `TypeRepr` and `Symbol` usage

2. **Test at compile time**
   ```scala
   // Test that invalid types are rejected
   "generateCodec[NotACaseClass]" shouldNot compile
   ```

3. **Provide good error messages**
   ```scala
   if !tpeSym.flags.is(Flags.Case) then
     report.errorAndAbort(
       s"${tpeSym.name} is not a case class. " +
       s"Consider using 'case class ${tpeSym.name}(...)'"
     )
   ```

### For Sealed Trait Support
If implementing polymorphic sealed trait support:

1. Detect sealed hierarchies at compile time
2. Generate discriminator field logic
3. Create runtime type resolver
4. Handle nested sealed traits
5. Support configurable discriminator strategies
6. Add comprehensive tests

Reference implementation pattern:
```scala
// Encoding:
writer.writeString(discriminatorField, caseClassName)
writer.writeName(fieldName)
encodeField(value)

// Decoding:
val discriminator = reader.readString(discriminatorField)
val childClass = discriminatorMap(discriminator)
decodeChild(childClass, reader)
```

---

## Questions or Problems?

- Open an [issue](https://github.com/mbannour/MongoScala3Codec/issues)
- Check existing [discussions](https://github.com/mbannour/MongoScala3Codec/discussions)
- Review the [README.md](README.md) and [ROADMAP.md](ROADMAP.md)

---

## Code of Conduct

- Be respectful and professional
- Welcome newcomers and help them learn
- Focus on constructive feedback
- Assume good intentions

---

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (Apache License 2.0).

---

Thank you for contributing to MongoScala3Codec! 🎉
