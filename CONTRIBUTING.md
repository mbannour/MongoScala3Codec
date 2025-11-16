# Contributing to MongoScala3Codec

First off, thank you for considering contributing to MongoScala3Codec! 🎉

It's people like you that make MongoScala3Codec a great tool for the Scala community.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Build](#how-to-build)
- [How to Test](#how-to-test)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Submitting Changes](#submitting-changes)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)

---

## Code of Conduct

This project and everyone participating in it is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [med.ali.bennour@gmail.com](mailto:med.ali.bennour@gmail.com).

---

## Getting Started

### Prerequisites

- **JDK 17** or higher
- **sbt 1.10+**
- **Scala 3.3.1+** (managed by sbt)
- **MongoDB** (for integration tests - optional, uses Testcontainers)

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/MongoScala3Codec.git
   cd MongoScala3Codec
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/mbannour/MongoScala3Codec.git
   ```

---

## How to Build

### Compile the Project

```bash
sbt compile
```

### Cross-Compile for Multiple Scala Versions

The project supports Scala 3.3.1, 3.4.x, 3.5.x, 3.6.x, and 3.7.x:

```bash
# Compile for all Scala versions
sbt +compile

# Compile for specific version
sbt ++3.7.1 compile
```

### Build Documentation

```bash
# Generate ScalaDoc
sbt doc

# Documentation is in target/scala-3.7.1/api/
```

---

## How to Test

### Run Unit Tests

```bash
# Run all unit tests
sbt test

# Run tests for specific Scala version
sbt ++3.7.1 test

# Run tests for all Scala versions
sbt +test
```

### Run Integration Tests

Integration tests use Testcontainers to spin up a real MongoDB instance:

```bash
# Run integration tests
sbt integrationTests/test

# Note: Docker must be running for integration tests
```

### Run Specific Test Suites

```bash
# Run a specific test file
sbt "testOnly io.github.mbannour.mongo.codecs.PropertyBasedCodecSpec"

# Run tests matching a pattern
sbt "testOnly *CodecSpec"
```

### Code Coverage

```bash
# Run tests with coverage
sbt clean coverage test coverageReport

# View coverage report
open target/scala-3.7.1/scoverage-report/index.html
```

**Coverage Goal:** ≥85% for core modules

---

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

Use descriptive branch names:
- `feature/add-bigint-support`
- `fix/option-encoding-issue`
- `docs/improve-quickstart`

### 2. Make Your Changes

- Write clear, concise code
- Follow existing code style (enforced by Scalafmt)
- Add tests for new features
- Update documentation as needed

### 3. Format Your Code

```bash
# Format all code
sbt scalafmtAll

# Check formatting
sbt scalafmtCheckAll
```

### 4. Run Scalafix

```bash
# Apply linting rules
sbt scalafixAll

# Check for issues
sbt "scalafixAll --check"
```

### 5. Run Tests

```bash
# Run unit tests
sbt test

# Run integration tests (if applicable)
sbt integrationTests/test
```

### 6. Commit Your Changes

Write clear commit messages following [Conventional Commits](https://www.conventionalcommits.org/):

```bash
git add .
git commit -m "feat: add support for BigInt codec"
git commit -m "fix: resolve Option[T] encoding with NoneHandling.Ignore"
git commit -m "docs: add examples for enum usage"
```

**Commit Message Format:**
```
<type>: <subject>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `chore`: Maintenance tasks

### 7. Push and Create Pull Request

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub.

---

## Coding Standards

### Scala Style

The project uses **Scalafmt** for consistent formatting. Configuration is in `.scalafmt.conf`.

**Key conventions:**
- **Indentation:** 2 spaces (no tabs)
- **Max line length:** 140 characters
- **Scala 3 syntax:** Use new syntax (`given`, `extension`, etc.)
- **Optional braces:** Enabled (use when appropriate)

### Code Quality

- **No unused imports:** Automatically removed by Scalafix
- **No vars:** Use immutable values (`val`) unless absolutely necessary
- **Explicit types:** For public APIs and complex expressions
- **Documentation:** All public APIs must have ScalaDoc comments

### Testing Standards

- **Test everything:** New features must include tests
- **Property-based tests:** Use ScalaCheck for codec round-trip tests
- **Golden tests:** Verify BSON structure for critical types
- **Integration tests:** Add for MongoDB driver interactions

**Test naming convention:**
```scala
"ComponentName" should "do something specific" in {
  // test code
}
```

---

## Submitting Changes

### Pull Request Checklist

Before submitting your PR, ensure:

- [ ] Code compiles without errors
- [ ] All tests pass (`sbt test`)
- [ ] Code is formatted (`sbt scalafmtAll`)
- [ ] Scalafix checks pass (`sbt "scalafixAll --check"`)
- [ ] New tests added for new features
- [ ] Documentation updated (if applicable)
- [ ] CHANGELOG.md updated (for significant changes)
- [ ] Commit messages follow Conventional Commits

### Pull Request Description

Provide a clear description of your changes:

```markdown
## Description
Brief description of what this PR does

## Motivation
Why is this change needed?

## Changes
- Change 1
- Change 2
- Change 3

## Testing
How has this been tested?

## Checklist
- [x] Tests pass
- [x] Documentation updated
- [x] Code formatted
```

### Code Review Process

1. **Automated checks:** GitHub Actions will run tests
2. **Maintainer review:** A maintainer will review your code
3. **Feedback:** Address any requested changes
4. **Approval:** Once approved, your PR will be merged

**Response time:** We aim to respond to PRs within 3-5 business days.

---

## Reporting Bugs

### Before Reporting

1. **Check existing issues:** Search [GitHub Issues](https://github.com/mbannour/MongoScala3Codec/issues)
2. **Try latest version:** Ensure you're using the latest release
3. **Check documentation:** Review [FAQ](docs/FAQ.md) and guides

### Bug Report Template

Use this template when reporting bugs:

```markdown
**Describe the bug**
A clear description of the bug.

**To Reproduce**
Steps to reproduce:
1. Define case class: `case class Foo(...)`
2. Register codec: `registry.register[Foo]`
3. See error: `...`

**Expected behavior**
What you expected to happen.

**Actual behavior**
What actually happened.

**Environment:**
- MongoScala3Codec version: [e.g., 0.0.6]
- Scala version: [e.g., 3.7.1]
- MongoDB version: [e.g., 6.0]
- JDK version: [e.g., 17]

**Code sample**
```scala
// Minimal reproducible example
case class Example(...)
```

**Error messages**
```
Full error message or stack trace
```

**Additional context**
Any other relevant information.
```

---

## Suggesting Enhancements

We welcome feature suggestions! Before suggesting:

1. **Check roadmap:** Review existing milestones and issues
2. **Check documentation:** Ensure feature doesn't already exist
3. **Be specific:** Provide clear use cases and examples

### Enhancement Template

```markdown
**Feature description**
Clear description of the proposed feature.

**Use case**
Why is this feature needed? What problem does it solve?

**Proposed API**
```scala
// Example of how the feature would be used
case class Example(...)
registry.newFeature[Example]()
```

**Alternatives considered**
Other ways to achieve the same goal.

**Additional context**
Any other relevant information.
```

---

## Project Structure

```
MongoScala3Codec/
├── src/
│   ├── main/scala/          # Source code
│   │   └── io/github/mbannour/
│   │       ├── bson/macros/ # BSON reader/writer macros
│   │       ├── fields/      # Field resolution
│   │       └── mongo/codecs/ # Codec generation
│   └── test/scala/          # Unit tests
├── integration/
│   └── src/test/scala/      # Integration tests
├── docs/                    # Documentation
├── examples/                # Example projects
└── project/                 # sbt configuration
```

---

## Development Tips

### REPL Development

Test your changes interactively:

```bash
sbt console

scala> import io.github.mbannour.mongo.codecs._
scala> case class Test(name: String)
scala> // test your code here
```

### Debugging Macros

Enable macro debugging:

```scala
// Add to build.sbt temporarily
scalacOptions += "-Xprint:typer"
```

### Running Specific Tests

```bash
# Run one test
sbt "testOnly *PropertyBasedCodecSpec"

# Run with specific test name
sbt "testOnly *PropertyBasedCodecSpec -- -z 'round-trip'"
```

### Continuous Testing

Watch mode for continuous testing:

```bash
sbt ~test
```

---

## Getting Help

- **Documentation:** Check [docs/](docs/) directory
- **GitHub Discussions:** Ask questions in [Discussions](https://github.com/mbannour/MongoScala3Codec/discussions)
- **Issues:** Report bugs in [Issues](https://github.com/mbannour/MongoScala3Codec/issues)
- **Email:** Contact maintainers at [med.ali.bennour@gmail.com](mailto:med.ali.bennour@gmail.com)

---

## Recognition

Contributors will be recognized in:
- **README.md** - Contributors section
- **Release notes** - Acknowledging contributions
- **GitHub insights** - Automatic contribution tracking

---

## License

By contributing to MongoScala3Codec, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for contributing! 🚀

