package org.dbu.library.ui

import org.dbu.library.model.Book
import org.dbu.library.model.Patron
import org.dbu.library.repository.LibraryRepository
import org.dbu.library.service.BorrowResult
import org.dbu.library.service.LibraryService

fun handleMenuAction(
    choice: String,
    service: LibraryService,
    repository: LibraryRepository
): Boolean {
    return when (choice.trim()) {
        "1" -> {
            addBook(service)
            true
        }
        "2" -> {
            registerPatron(repository)
            true
        }
        "3" -> {
            borrowBook(service)
            true
        }
        "4" -> {
            returnBook(service)
            true
        }
        "5" -> {
            search(service)
            true
        }
        "6" -> {
            listAllBooks(repository)
            true
        }
        "0" -> false
        else -> {
            println("Invalid option")
            true
        }
    }
}

private fun readRequired(prompt: String): String {
    while (true) {
        print(prompt)
        val value = readlnOrNull()?.trim()
        if (!value.isNullOrBlank()) return value
        println("Input cannot be empty.")
    }
}

private fun addBook(service: LibraryService) {
    println("\n--- Add Book ---")
    val isbn = readRequired("ISBN: ")
    val title = readRequired("Title: ")
    val author = readRequired("Author: ")
    val year = readRequired("Publication year: ").toIntOrNull()

    if (year == null || year <= 0) {
        println("Invalid publication year.")
        return
    }

    if (service.addBook(Book(isbn, title, author, year))) {
        println("Book added successfully.")
    } else {
        println("A book with that ISBN already exists.")
    }
}

private fun registerPatron(repository: LibraryRepository) {
    println("\n--- Register Patron ---")
    val id = readRequired("Patron ID: ")
    val name = readRequired("Patron name: ")

    if (repository.addPatron(Patron(id, name))) {
        println("Patron registered successfully.")
    } else {
        println("A patron with that ID already exists.")
    }
}

private fun borrowBook(service: LibraryService) {
    println("\n--- Borrow Book ---")
    val patronId = readRequired("Patron ID: ")
    val isbn = readRequired("Book ISBN: ")

    when (service.borrowBook(patronId, isbn)) {
        BorrowResult.SUCCESS -> println("Book borrowed successfully.")
        BorrowResult.BOOK_NOT_FOUND -> println("Book not found.")
        BorrowResult.PATRON_NOT_FOUND -> println("Patron not found.")
        BorrowResult.NOT_AVAILABLE -> println("Book is not available.")
        BorrowResult.LIMIT_REACHED -> println("Borrowing limit reached.")
    }
}

private fun returnBook(service: LibraryService) {
    println("\n--- Return Book ---")
    val patronId = readRequired("Patron ID: ")
    val isbn = readRequired("Book ISBN: ")

    if (service.returnBook(patronId, isbn)) {
        println("Book returned successfully.")
    } else {
        println("Return failed. Check the patron ID and ISBN.")
    }
}

private fun search(service: LibraryService) {
    println("\n--- Search Books ---")
    val query = readRequired("Enter title or author: ")
    val books = service.search(query)

    if (books.isEmpty()) {
        println("No matching books found.")
        return
    }

    books.forEach { book ->
        val status = if (book.isAvailable) "Available" else "Borrowed"
        println("${book.title} | ${book.author} | ISBN: ${book.isbn} | Year: ${book.year} | $status")
    }
}

private fun listAllBooks(repository: LibraryRepository) {
    println("\n--- All Books ---")
    val books = repository.getAllBooks()

    if (books.isEmpty()) {
        println("No books in the library.")
        return
    }

    books.forEach { book ->
        val status = if (book.isAvailable) "Available" else "Borrowed"
        println("${book.title} | ${book.author} | ISBN: ${book.isbn} | Year: ${book.year} | $status")
    }
}

