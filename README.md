# 📚 Library Management System (LMS)

This is a **Java + SQLite** based Library Management System that provides separate dashboards for **Students**, **Librarians**, and **Admins**.  
The system allows managing users, books, borrowing/returning, reservations, fines, and generating reports.

---

## Project Description  

The Library Management System (LMS) is a software application that helps manage the 
daily operations of a library. It automates tasks such as adding books, searching for books,
borrowing, returning, and reserving them. 
The system has three main users: Students who search and borrow books, 
Librarians who handle borrow/return/reservations, 
and Admins who manage the overall system and generate reports. 

---

 ## ✨ Project Objectives  
 
• Make it easy for students to search and find books quickly.  
• Reduce manual work and avoid errors in borrow/return records. 
• Track the status of books (available, borrowed, reserved, lost).  
• Automatically calculate late return fines.  
• Provide useful reports and statistics for better decision-making. 
• Improve the overall user experience with a simple interface. 

---

## 🚀 Features

### 🔹 Student Dashboard
- 🔍 Search for books  
- 📖 Borrow books  
- ↩️ Return books (with fine calculation)  
- 📌 Reserve books  

### 🔹 Librarian Dashboard
- 📚 Manage books (add, edit, delete)  
- 📖 Manage borrow/return operations  
- 📌 Manage reservations  
- 💰 Manage fines  
- 📊 Generate reports  

### 🔹 Admin Dashboard
- 👤 Manage users (add, edit, delete)  
- 🔐 Manage roles (Student / Librarian / Admin)  
- 💾 Backup database  
- 📊 System reports & statistics  
- ⚙️ System settings  


🎥 [Click here to watch the demo video](https://drive.google.com/drive/folders/15DwBRWbbKADbcFME3JBb52dFQ4w437oU)

---

## 🛠️ Technologies Used
- **Java (Swing)** for GUI  
- **SQLite** for database  
- **JDBC** for database connection  

---

## Before You Run 🚀

1. First, please **Star ⭐** the repository and then click **Fork ** (you can find the Fork button at the top-right of this page).  
2. After that, clone the project to your local machine using this code:  
   ```bash
   git clone https://github.com/myarnwas/Library-Management-System-LMS-.git

---

## 📂 How to Run

1. Make sure you have **JDK 8+** installed.  
2. The required SQLite JDBC driver (`sqlite-jdbc-3.42.0.0.jar`) is already included in the project.  
3. Open terminal in the project folder.  
4. Compile the project:
   ```bash
   javac -cp ".;sqlite-jdbc-3.42.0.0.jar" LibrarySystem.java

5. Run the project:
   ```bash
   java  -cp ".;sqlite-jdbc-3.42.0.0.jar" LibrarySystem

---

## 📊 Notes

•	The system will automatically create the library.db SQLite database file with the required tables if it doesn’t exist.

•	Each dashboard opens as a separate form with real functionality connected to the database.

---

## 💻 Author

Developed by **Mayar Waleed Nawas**
