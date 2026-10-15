# Student Placement Management System — DBMS PAT-4

A complete, lightweight, student-level web application built for the **Database Management Systems (DBMS) PAT-4 practical examination**. 

The system provides a student placement portal that automates student profile registration, recruitment drive management, academic eligibility evaluation (CGPA & backlogs), job application processing, and direct demonstration of DBMS SQL queries (**DDL, DML, DQL, JOINs, GROUP BY**).

---

## 1. Project Overview & Architecture

- **Domain:** Student Placement Management
- **Backend:** Java (Built-in `com.sun.net.httpserver.HttpServer`) — No bulky frameworks (no Spring Boot, no Node.js required)
- **Database:** Oracle Database Express Edition (Oracle XE 11g)
- **Database Connectivity:** Java Database Connectivity (JDBC) using Oracle Thin Driver (`ojdbc6.jar`)
- **Frontend:** Vanilla HTML5, CSS3, and JavaScript
- **Server Port:** `8086`
- **Database Schema:** `PLACEMENT_USER`

```
+------------------------------------+
|       Browser (HTML/CSS/JS)        |
+------------------------------------+
                  |  HTTP REST / Port 8086
                  v
+------------------------------------+
|      Java Built-in HTTP Server     |
|   (PlacementServer / Routing / Log)|
+------------------------------------+
                  |  JDBC (ojdbc6.jar)
                  v
+------------------------------------+
|       Oracle Database XE (1521)    |
|   Schema: PLACEMENT_USER           |
|   Tables: STUDENTS, COMPANIES,     |
|           DRIVES, APPLICATIONS,    |
|           USERS                    |
+------------------------------------+
```

---

## 2. Directory Structure

```
PlacementManagementSystem/
├── .gitignore               # Excludes secrets, class files, and logs
├── README.md                # Full installation, setup, and viva guide
├── compile.bat              # Batch script to compile Java backend
├── run.bat                  # Batch script to launch backend on port 8086
├── config/
│   ├── db.properties        # Active local database & port settings
│   └── db.properties.example # Template config for version control
├── lib/
│   └── ojdbc6.jar           # Oracle XE JDBC Driver
├── sql/
│   ├── schema.sql           # DDL schema creation and initial sample records
│   └── queries.sql          # Working PAT-4 SQL demonstration queries
├── src/
│   └── com/
│       └── placement/
│           ├── db/
│           │   └── DBConnection.java   # Config-driven JDBC connection manager
│           └── server/
│               └── PlacementServer.java # HTTP API server and request logger
└── web/
    ├── index.html           # Single-page tabbed user interface
    ├── css/
    │   └── styles.css       # Clean navy-blue and teal student-level theme
    └── js/
        └── app.js           # Client-side routing, API calls, and SQL executor
```

---

## 3. Prerequisites & Environment Check

1. **Verify Java Installation:**
   ```powershell
   java -version
   javac -version
   ```
   *Compatible with Java 8, 11, 17, 21+.*

2. **Verify Oracle Database & SQL*Plus:**
   ```powershell
   sqlplus -version
   ```
   *Ensure Oracle Database XE service is running on port `1521`.*

---

## 4. Oracle Database Setup (One-Time Setup)

### Step 4.1: Create Dedicated Placement User (as SYSDBA)
Open your terminal or command prompt and run:
```powershell
sqlplus / as sysdba
```
Inside SQL*Plus, execute:
```sql
CREATE USER PLACEMENT_USER IDENTIFIED BY placement123;
GRANT CONNECT, RESOURCE TO PLACEMENT_USER;
GRANT UNLIMITED TABLESPACE TO PLACEMENT_USER;
EXIT;
```

### Step 4.2: Run the Schema Creation Script
Run the script as `PLACEMENT_USER`:
```powershell
sqlplus PLACEMENT_USER/placement123@localhost:1521/xe
```
Then execute:
```sql
SET DEFINE OFF;
@sql/schema.sql;
EXIT;
```
This automatically creates:
- `USERS` table (with demo credentials)
- `STUDENTS` table (with check constraints for CGPA and backlogs)
- `COMPANIES` table
- `PLACEMENT_DRIVES` table (linked to COMPANIES with foreign key)
- `PLACEMENT_APPLICATIONS` table (with unique constraints and status tracking)
- Sequences for automatic primary key generation (`USERS_SEQ`, `STUDENTS_SEQ`, etc.)
- Rich sample data for second-year placement scenarios.

---

## 5. Configuration Setup

The database credentials and server port are managed in `config/db.properties`:

```properties
server.port=8086
db.url=jdbc:oracle:thin:@localhost:1521:xe
db.user=PLACEMENT_USER
db.password=placement123
db.driver=oracle.jdbc.OracleDriver
```

> **Security Note:** Passwords are kept in `config/db.properties` and are ignored in git via `.gitignore`. Never commit actual passwords to version control.

---

## 6. How to Compile and Run

### Step 6.1: Compile Backend
Double-click `compile.bat` or run:
```cmd
compile.bat
```
*Output: `[SUCCESS] Backend compiled successfully into bin/ folder.`*

### Step 6.2: Start Backend Server
Double-click `run.bat` or run:
```cmd
run.bat
```

You will see terminal logging:
```
==================================================
STUDENT PLACEMENT MANAGEMENT SYSTEM
Backend Server Started Successfully
Server URL    : http://localhost:8086
Database User : PLACEMENT_USER
JDBC Driver   : Oracle JDBC Thin Driver
Port Used     : 8086
==================================================
```

### Step 6.3: Access the Portal
Open any web browser and visit:
[http://localhost:8086](http://localhost:8086)

---

## 7. Login Credentials (from Oracle USERS table)

| Role | Username | Password | Access Level |
| :--- | :--- | :--- | :--- |
| **Director / Admin** | `admin` | `admin123` | Full placement management |
| **Placement Officer** | `officer` | `officer123` | Student registration & drive processing |

Authentication dynamically queries the Oracle database table `USERS` using prepared statements.

---

## 8. Features & PAT-4 Teacher Requirements Checklist

| Requirement | Implementation Details |
| :--- | :--- |
| **1. Login Page** | Database-backed login modal validating credentials against Oracle `USERS` table with error alerts. |
| **2. Dashboard Cards** | Real-time counts of Total Students, Placed Students, Partner Companies, and Applications retrieved from Oracle. |
| **3. Student Details** | Form to insert new student records with constraints (CGPA 0.0-10.0, unique Reg No & Email) + tabular view. |
| **4. Companies & Drives** | Register company profiles and schedule placement drives with job roles, LPA packages, and eligibility criteria. |
| **5. Eligibility Processing** | Automated logic comparing student CGPA and backlogs against company minimum criteria (e.g. Rahul Verma CGPA 6.80 vs Infosys Min 8.00 &rarr; Not Eligible; Arjun Sharma CGPA 8.75 vs TCS Min 7.00 &rarr; Eligible). |
| **6. Placement Applications** | Submit applications and update student status (`APPLIED`, `SHORTLISTED`, `PLACED`, `REJECTED`). Marking `PLACED` automatically updates student record. |
| **7. Multi-Table JOIN View** | Full application directory showing student details joined with company drives and status. |
| **8. SQL Demonstration** | Modular categorized DBMS console for **DDL**, **DML**, **DQL**, **JOINs**, and **GROUP BY** with structured tabular results. |
| **9. DB Connectivity** | Live connection health status display reporting Oracle version, current user, and port. |
| **10. Backend Logging** | Structured terminal logs with HTTP method, URI, response code, execution duration in milliseconds, and error codes (`ORA-XXXXX`). |

---

## 9. Viva Questions & Explanations

1. **How does Java communicate with Oracle Database XE?**
   - Java uses **JDBC (Java Database Connectivity)**. The `DBConnection` class loads `oracle.jdbc.OracleDriver` from `lib/ojdbc6.jar` and opens a connection using the thin client URL `jdbc:oracle:thin:@localhost:1521:xe`.

2. **Why use `PreparedStatement` instead of `Statement`?**
   - Prepared statements pre-compile the SQL statement on the database engine. User inputs are passed as parameters (e.g. `pstmt.setString(1, regNo)`), which prevents **SQL Injection** and improves performance.

3. **How does the Placement Eligibility logic work?**
   - The server queries the student's `CGPA` and `BACKLOGS` along with the drive's `MIN_CGPA` and `MAX_BACKLOGS`. A student is eligible if `CGPA >= MIN_CGPA` and `BACKLOGS <= MAX_BACKLOGS`.

4. **How are credentials and configuration managed securely?**
   - Credentials and port settings reside in `config/db.properties`, which is excluded from version control via `.gitignore`. A sample template `config/db.properties.example` is provided for configuration.
