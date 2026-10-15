-- ====================================================================
-- STUDENT PLACEMENT MANAGEMENT SYSTEM - DBMS PAT-4 QUERIES
-- Schema: PLACEMENT_USER | Database: Oracle Database 11g Express Edition
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. DATA DEFINITION LANGUAGE (DDL)
-- --------------------------------------------------------------------

-- A. Create Table Example (Primary Key, Foreign Key, Check & Unique Constraints)
CREATE TABLE CERTIFICATIONS (
    CERT_ID NUMBER PRIMARY KEY,
    STUDENT_ID NUMBER NOT NULL,
    CERT_NAME VARCHAR2(100) NOT NULL,
    ISSUING_ORG VARCHAR2(100) NOT NULL,
    ISSUE_DATE DATE,
    CONSTRAINT FK_CERT_STUDENT FOREIGN KEY (STUDENT_ID) REFERENCES STUDENTS(STUDENT_ID) ON DELETE CASCADE
);

-- B. Alter Table Example (Adding a column to existing table)
ALTER TABLE STUDENTS ADD (PROJECT_TITLE VARCHAR2(150));

-- C. Alter Table Example (Modifying column or adding constraint)
ALTER TABLE STUDENTS MODIFY (PROJECT_TITLE VARCHAR2(200));

-- --------------------------------------------------------------------
-- 2. DATA MANIPULATION LANGUAGE (DML)
-- --------------------------------------------------------------------

-- A. Insert Records (DML: INSERT)
INSERT INTO STUDENTS (STUDENT_ID, REGISTER_NO, NAME, DEPARTMENT, CGPA, BACKLOGS, EMAIL, PHONE, STATUS)
VALUES (STUDENTS_SEQ.NEXTVAL, '23BCSE110', 'Ananya Deshmukh', 'Computer Science', 8.90, 0, 'ananya.d@campus.edu', '9811223344', 'UNPLACED');

-- B. Update Records (DML: UPDATE)
UPDATE STUDENTS 
SET STATUS = 'PLACED' 
WHERE REGISTER_NO = '23BCSE110';

-- C. Safe Update of Placement Status based on Application results
UPDATE PLACEMENT_APPLICATIONS 
SET STATUS = 'SHORTLISTED', REMARKS = 'Shortlisted for technical round'
WHERE APPLICATION_ID = 3;

-- D. Delete Example (DML: DELETE with specific safe criteria)
DELETE FROM PLACEMENT_APPLICATIONS 
WHERE REMARKS = 'Test application to delete';

-- --------------------------------------------------------------------
-- 3. DATA QUERY LANGUAGE (DQL)
-- --------------------------------------------------------------------

-- A. Basic Selection with Projection
SELECT STUDENT_ID, REGISTER_NO, NAME, DEPARTMENT, CGPA 
FROM STUDENTS;

-- B. Filtering with WHERE Clause (Relational & Logical operators)
SELECT REGISTER_NO, NAME, CGPA, BACKLOGS 
FROM STUDENTS 
WHERE CGPA >= 8.0 AND BACKLOGS = 0;

-- C. Sorting with ORDER BY Clause (Ascending and Descending)
SELECT NAME, DEPARTMENT, CGPA 
FROM STUDENTS 
ORDER BY CGPA DESC;

-- D. Placement Drives with Salary Packaging Ordering
SELECT d.JOB_ROLE, c.COMPANY_NAME, d.PACKAGE_LPA, d.MIN_CGPA 
FROM PLACEMENT_DRIVES d 
JOIN COMPANIES c ON d.COMPANY_ID = c.COMPANY_ID 
ORDER BY d.PACKAGE_LPA DESC;

-- E. Relational Multi-Table JOIN (Students + Applications + Drives + Companies)
SELECT 
    s.REGISTER_NO,
    s.NAME AS STUDENT_NAME,
    s.CGPA,
    c.COMPANY_NAME,
    d.JOB_ROLE,
    d.PACKAGE_LPA,
    a.ELIGIBILITY_STATUS,
    a.STATUS AS APPLICATION_STATUS
FROM PLACEMENT_APPLICATIONS a
JOIN STUDENTS s ON a.STUDENT_ID = s.STUDENT_ID
JOIN PLACEMENT_DRIVES d ON a.DRIVE_ID = d.DRIVE_ID
JOIN COMPANIES c ON d.COMPANY_ID = c.COMPANY_ID
ORDER BY a.APPLICATION_ID DESC;

-- F. Aggregation & Summarization with GROUP BY, COUNT, and AVG
SELECT 
    c.COMPANY_NAME,
    COUNT(a.APPLICATION_ID) AS TOTAL_APPLICANTS,
    ROUND(AVG(s.CGPA), 2) AS AVERAGE_APPLICANT_CGPA
FROM COMPANIES c
JOIN PLACEMENT_DRIVES d ON c.COMPANY_ID = d.COMPANY_ID
LEFT JOIN PLACEMENT_APPLICATIONS a ON d.DRIVE_ID = a.DRIVE_ID
LEFT JOIN STUDENTS s ON a.STUDENT_ID = s.STUDENT_ID
GROUP BY c.COMPANY_NAME;

-- G. Department-wise Placement Statistics (GROUP BY + HAVING)
SELECT 
    DEPARTMENT,
    COUNT(*) AS TOTAL_STUDENTS,
    ROUND(AVG(CGPA), 2) AS AVG_DEPT_CGPA
FROM STUDENTS
GROUP BY DEPARTMENT
HAVING COUNT(*) > 0
ORDER BY AVG_DEPT_CGPA DESC;
