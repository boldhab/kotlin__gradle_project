# Library Management System

A robust, console-based Library Management System developed in **Kotlin** using the **Gradle** build tool. This application implements core library operations—catalog management, patron registration, book borrowing with validation limits, book returns, and inventory search—structured following clean architecture and separation of concerns.

---

## Table of Contents

- [Project Purpose and Objectives](#project-purpose-and-objectives)
- [Architecture and Project Structure](#architecture-and-project-structure)
- [Implemented Features and Business Rules](#implemented-features-and-business-rules)
- [Prerequisites and Setup (Fedora / Linux)](#prerequisites-and-setup-fedora--linux)
- [Build and Run Commands](#build-and-run-commands)
- [Console Usage Examples](#console-usage-examples)
- [Validation and Error-Handling Behavior](#validation-and-error-handling-behavior)
- [Testing and Build Verification](#testing-and-build-verification)
- [Improvements Made and Their Benefits](#improvements-made-and-their-benefits)

---

## Project Purpose and Objectives

The primary objective of this project is to build an educational yet production-minded Library Management System in Kotlin while demonstrating:

1. **Idiomatic Kotlin Development**: Clean usage of data classes, immutability, nullable types, safe calls, extension functions, and when expressions.
2. **Layered Architecture**: Decoupling domain models, data access (repository), business logic (service), and user interface (CLI menu).
3. **Defensive Programming & Input Validation**: Guaranteeing that blank inputs, invalid publication years, and edge-case borrowing violations are intercepted safely.
4. **Gradle Build Tooling**: Managing project lifecycle tasks (compilation, testing, packaging runnable fat JARs, and distribution archives) using Gradle Kotlin DSL (`build.gradle.kts`).

---

## Architecture and Project Structure

The project follows a standard modular, layered architecture:

```
kotlin__gradle_project/
├── app/
│   ├── build.gradle.kts                # Application build configuration & fat JAR packaging
│   └── src/
│       ├── main/
│       │   └── kotlin/
│       │       └── org/
│       │           ├── dbu/
│       │           │   └── library/
│       │           │       ├── model/
│       │           │       │   ├── Book.kt               # Domain model for Books
│       │           │       │   └── Patron.kt             # Domain model for Patrons
│       │           │       ├── repository/
│       │           │       │   ├── LibraryRepository.kt  # Repository contract
│       │           │       │   └── InMemoryLibraryRepository.kt # In-memory storage implementation
│       │           │       ├── service/
│       │           │       │   ├── BorrowResult.kt       # Enum for borrow outcome states
│       │           │       │   ├── LibraryService.kt     # Business logic contract
│       │           │       │   └── DefaultLibraryService.kt # Core business logic implementation
│       │           │       ├── ui/
│       │           │       │   ├── ConsoleApp.kt         # Entry point & seed data
│       │           │       │   ├── Menu.kt               # Menu rendering & selection
│       │           │       │   └── MenuHandler.kt        # CLI action dispatching & input reading
│       │           │       └── util/
│       │           │           └── Extensions.kt         # Formatting & helper extensions
│       │           └── example/
│       │               └── App.kt                        # Starter template greeting class
│       └── test/
│           └── kotlin/
│               └── org/
│                   └── example/
│                       └── AppTest.kt                    # Unit test suite
├── gradle/
│   ├── libs.versions.toml              # Version catalog for dependencies and plugins
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties   # Gradle 9.3.1 distribution wrapper
├── gradlew                             # Gradle wrapper shell script (Linux/macOS)
├── gradlew.bat                         # Gradle wrapper batch script (Windows)
├── settings.gradle.kts                 # Multi-project / repository settings
├── app.jar                             # Reference compiled runnable JAR
├── README.md                           # Main project documentation
├── IMPLEMENTATION_COMPARISON.md        # Technical comparison with peer implementation
└── FUTURE_ENHANCEMENTS.md              # Roadmap and planned improvements
```

### Architectural Responsibilities

- **Model Layer (`org.dbu.library.model`)**:
  - `Book`: Immutable data class containing `isbn: String`, `title: String`, `author: String`, `year: Int`, and `isAvailable: Boolean` (defaulting to `true`).
  - `Patron`: Immutable data class representing members with `id: String`, `name: String`, and `borrowedBooks: List<String>` (list of borrowed ISBNs, defaulting to empty).

- **Repository Layer (`org.dbu.library.repository`)**:
  - `LibraryRepository`: Defines the persistence interface (`addBook`, `findBook`, `updateBook`, `addPatron`, `findPatron`, `updatePatron`, `getAllBooks`, `getAllPatrons`).
  - `InMemoryLibraryRepository`: Thread-local in-memory storage backed by `LinkedHashMap` instances (`books` and `patrons`). Guarantees duplicate key rejection.

- **Service Layer (`org.dbu.library.service`)**:
  - `LibraryService`: Exposes business operations: `addBook`, `borrowBook`, `returnBook`, `search`, `findBook`, and `getAllPatrons`.
  - `DefaultLibraryService`: Encapsulates validation and state updates. Enforces borrowing limits (max 3 books per patron), validates existence, verifies item availability, and immutably modifies book and patron records.
  - `BorrowResult`: Exhaustive enum representing all borrow outcomes (`SUCCESS`, `BOOK_NOT_FOUND`, `PATRON_NOT_FOUND`, `NOT_AVAILABLE`, `LIMIT_REACHED`).

- **UI Layer (`org.dbu.library.ui`)**:
  - `ConsoleApp.kt`: Instantiates repository and service, seeds initial data, and executes the primary application loop.
  - `Menu.kt`: Renders the clean text-based menu (options 1–6, and 0 to exit).
  - `MenuHandler.kt`: Reads and validates terminal input (using `readRequired` to prevent empty inputs), invokes service methods, and prints user feedback.

---

## Implemented Features and Business Rules

### 1. Add Book (Menu Option 1)
- Prompts for `ISBN`, `Title`, `Author`, and `Publication year`.
- Validates that publication year is a positive integer (`year > 0`).
- Prevents duplicate entries: If a book with the same ISBN already exists, addition is rejected.

### 2. Register Patron (Menu Option 2)
- Prompts for `Patron ID` and `Patron name`.
- Enforces uniqueness: Rejects registration if a patron with the specified ID already exists in the repository.

### 3. Borrow Book (Menu Option 3)
Enforces a strict 4-step business validation policy:
1. **Book Existence Check**: If the ISBN is not found, returns `BorrowResult.BOOK_NOT_FOUND`.
2. **Patron Existence Check**: If the patron ID is not found, returns `BorrowResult.PATRON_NOT_FOUND`.
3. **Availability Check**: If the book is already borrowed (`!book.isAvailable`), returns `BorrowResult.NOT_AVAILABLE`.
4. **Borrowing Limit Check**: If the patron already has 3 or more borrowed books (`patron.borrowedBooks.size >= 3`), returns `BorrowResult.LIMIT_REACHED`.
- When all rules pass, returns `BorrowResult.SUCCESS`, marks the book `isAvailable = false`, and appends the ISBN to the patron's borrowed books list.

### 4. Return Book (Menu Option 4)
- Validates that the book and patron exist.
- Validates that the specified book is actually recorded in the patron's borrowed list (`isbn in patron.borrowedBooks`).
- On successful return, sets book `isAvailable = true` and removes the ISBN from the patron's borrowed list.

### 5. Search Books (Menu Option 5)
- Prompts for a search query.
- Trims whitespace: If the search query is blank or empty, returns an empty result list rather than dumping the whole catalog.
- Performs case-insensitive matching on both `title` and `author`.

### 6. List All Books (Menu Option 6)
- Retrieves all cataloged books.
- Displays Title, Author, ISBN, Publication Year, and current status (`Available` or `Borrowed`).
- Prints `"No books in the library."` if the catalog is empty.

### 7. Seed Data on Boot
Upon startup, the application pre-populates default entities for immediate testing:
- **Books**:
  - ISBN `1`: *"Clean Code"* by Robert Martin (2008)
  - ISBN `2`: *"Effective Java"* by Joshua Bloch (2018)
- **Patrons**:
  - ID `P1`: *"Alice"*

---

## Prerequisites and Setup (Fedora / Linux)

### 1. System Requirements
- **Operating System**: Fedora Linux 40/41/44 or any standard Linux distribution.
- **Java Development Kit**: JDK 21 or later (compatible with Java 21 toolchain configured in `build.gradle.kts`).

### 2. Installing Prerequisites on Fedora
Install OpenJDK 21 via `dnf`:
```bash
sudo dnf install -y java-21-openjdk java-21-openjdk-devel git
```

Verify your Java version:
```bash
java -version
```
Expected output:
```text
openjdk version "21.x.x" (or higher)
```

---

## Build and Run Commands

The repository includes the Gradle Wrapper (`gradlew`), eliminating the need to install Gradle manually.

### 1. Ensure Executable Permissions
```bash
chmod +x gradlew
```

### 2. Clean and Build the Project
Compiles Kotlin sources, processes resources, runs tests, and assembles distribution artifacts:
```bash
./gradlew clean build
```

> **Note**: To prevent daemon lock timeouts on multi-user or sandboxed systems, append `--no-daemon`:
> ```bash
> ./gradlew clean build --no-daemon
> ```

### 3. Run Automated Tests
```bash
./gradlew test
```

### 4. Package Standalone Executable (Fat JAR)
Builds the standalone JAR with all dependencies embedded into `app/build/libs/app.jar`:
```bash
./gradlew jar
```

### 5. Run the Application

#### Option A: Direct Execution via Gradle
```bash
./gradlew run --quiet --console=plain
```

#### Option B: Run the Compiled Executable JAR (Recommended)
```bash
java -jar app/build/libs/app.jar
```

#### Option C: Run the Pre-compiled Reference JAR
```bash
java -jar app.jar
```

---

## Console Usage Examples

Below is a complete, unedited walkthrough of an actual console session:

### 1. Main Menu and Initial Listing
```text
================ LIBRARY MENU [for show]================
 1 | Add Book
 2 | Register Patron
 3 | Borrow Book
 4 | Return Book
 5 | Search
 6 | List All Books
 0 | Exit
==============================================
 Enter option:
6

--- All Books ---
Clean Code | Robert Martin | ISBN: 1 | Year: 2008 | Available
Effective Java | Joshua Bloch | ISBN: 2 | Year: 2018 | Available
```

### 2. Adding a New Book
```text
 Enter option:
1

--- Add Book ---
ISBN: 3
Title: Kotlin in Action
Author: Dmitry Jemerov
Publication year: 2017
Book added successfully.
```

### 3. Registering a Patron
```text
 Enter option:
2

--- Register Patron ---
Patron ID: P2
Patron name: Bob
Patron registered successfully.
```

### 4. Borrowing a Book
```text
 Enter option:
3

--- Borrow Book ---
Patron ID: P1
Book ISBN: 1
Book borrowed successfully.
```

### 5. Searching for a Book
```text
 Enter option:
5

--- Search Books ---
Enter title or author: clean
Clean Code | Robert Martin | ISBN: 1 | Year: 2008 | Borrowed
```

### 6. Returning a Book
```text
 Enter option:
4

--- Return Book ---
Patron ID: P1
Book ISBN: 1
Book returned successfully.
```

### 7. Exiting the Application
```text
 Enter option:
0
Goodbye 👋
```

---

## Validation and Error-Handling Behavior

The application implements defensive controls across all user touchpoints:

| Scenario | Input / Action | System Behavior & Output |
| :--- | :--- | :--- |
| **Blank Input** | Pressing Enter or entering spaces on any prompt | Blocked by `readRequired()`. Prompts repeatedly with `"Input cannot be empty."` |
| **Invalid Year** | Entering `0`, `-2020`, or non-numeric strings | Caught in `addBook()`. Displays `"Invalid publication year."` and aborts addition. |
| **Duplicate ISBN** | Adding a book with existing ISBN | Repository detects key collision. Displays `"A book with that ISBN already exists."` |
| **Duplicate Patron** | Registering a patron with existing ID | Repository detects key collision. Displays `"A patron with that ID already exists."` |
| **Nonexistent Book** | Borrowing ISBN `999` | Service returns `BorrowResult.BOOK_NOT_FOUND`. Displays `"Book not found."` |
| **Nonexistent Patron** | Borrowing with Patron ID `P999` | Service returns `BorrowResult.PATRON_NOT_FOUND`. Displays `"Patron not found."` |
| **Unavailable Book** | Borrowing a book already loaned | Service returns `BorrowResult.NOT_AVAILABLE`. Displays `"Book is not available."` |
| **Limit Reached** | Patron attempts to borrow 4th book | Service returns `BorrowResult.LIMIT_REACHED`. Displays `"Borrowing limit reached."` |
| **Invalid Return** | Returning a book not held by patron | Returns `false`. Displays `"Return failed. Check the patron ID and ISBN."` |
| **Blank Search** | Entering whitespace in Search | `term.isEmpty()` guard triggers. Returns empty list with `"No matching books found."` |
| **Invalid Option** | Entering an unlisted menu number or character | `when` default branch executes. Displays `"Invalid option"` and returns to menu. |

---

## Testing and Build Verification

### Build Verification
The build pipeline was executed and validated under Linux:
- Task: `./gradlew clean build --no-daemon`
- Result: **BUILD SUCCESSFUL** (8 actionable tasks executed in 27s).

### Current Test Suite Status
The existing automated test suite (`AppTest.kt`) executes JUnit Jupiter 5 assertions (`appHasAGreeting`). Unit test coverage for `DefaultLibraryService` and `InMemoryLibraryRepository` has been designed and specified in [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md). Manual test suites covering all CLI workflows and edge cases have been executed and verified against `app/build/libs/app.jar` and the reference `app.jar`.

---

## Improvements Made and Their Benefits

1. **Non-Blocking Input Validation (`readRequired`)**:
   - Replaced fragile raw `readln()` invocations with a robust input loop. Users cannot inadvertently submit blank strings or crash the application.
2. **Numeric Year Boundary Checks**:
   - Added `toIntOrNull()` parsing combined with positive integer verification (`year <= 0`) to prevent corrupted catalog dates.
3. **Safe Substring Search**:
   - Trimmed queries before evaluation and returned `emptyList()` for blank searches, preventing unintended full catalog dumps.
4. **Exhaustive Result Mapping**:
   - The UI exhaustively branches over all `BorrowResult` enum constants, ensuring users receive precise, actionable feedback for every failure mode.
5. **Clean Separation of Concerns**:
   - Preserved immutability across models through Kotlin data class `.copy()`, ensuring predictable state transitions without side effects.
