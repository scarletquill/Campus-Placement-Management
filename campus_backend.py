import mysql.connector
from mysql.connector import Error

# ==========================================
# 1. DATABASE CONFIGURATION
# ==========================================
DB_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": "your_mysql_password",  # <-- Replace with your MySQL root password
    "database": "campus_placement_db"
}


def get_connection(include_db=True):
    """Creates and returns a MySQL database connection."""
    config = DB_CONFIG.copy()
    if not include_db:
        config.pop("database", None)
    return mysql.connector.connect(**config)


# ==========================================
# 2. DATABASE, TABLE & PL/SQL INITIALIZATION
# ==========================================
def init_db():
    """
    Initializes the database, 3NF tables, Trigger, and Cursor-based Stored Procedure.
    Safe to run multiple times even if your DBMS teammate already created the tables.
    """
    conn = None
    cursor = None
    try:
        # Step 1: Create Database if it doesn't exist
        conn = get_connection(include_db=False)
        cursor = conn.cursor()
        cursor.execute(f"CREATE DATABASE IF NOT EXISTS {DB_CONFIG['database']}")
        cursor.close()
        conn.close()

        # Step 2: Connect to the target database and create tables
        conn = get_connection(include_db=True)
        cursor = conn.cursor()

        # Student Table: Student_ID (PK), Name, Branch, CGPA, Status
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS Student (
                Student_ID INT PRIMARY KEY,
                Name VARCHAR(100) NOT NULL,
                Branch VARCHAR(50) NOT NULL,
                CGPA DECIMAL(4, 2) NOT NULL CHECK (CGPA >= 0.0 AND CGPA <= 10.0),
                Status VARCHAR(30) DEFAULT 'Unplaced'
            )
        """)

        # Company Table: Company_ID (PK), Name, Role, Package
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS Company (
                Company_ID INT PRIMARY KEY,
                Name VARCHAR(100) NOT NULL,
                Role VARCHAR(100) NOT NULL,
                Package DECIMAL(10, 2) NOT NULL CHECK (Package >= 0.0)
            )
        """)

        # Application Table: App_ID (PK), Student_ID (FK), Company_ID (FK)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS Application (
                App_ID INT AUTO_INCREMENT PRIMARY KEY,
                Student_ID INT NOT NULL,
                Company_ID INT NOT NULL,
                UNIQUE KEY unique_application (Student_ID, Company_ID),
                CONSTRAINT fk_student FOREIGN KEY (Student_ID) 
                    REFERENCES Student(Student_ID) ON DELETE CASCADE,
                CONSTRAINT fk_company FOREIGN KEY (Company_ID) 
                    REFERENCES Company(Company_ID) ON DELETE CASCADE
            )
        """)

        # Step 3: PL/SQL Concept 1 - TRIGGER
        # Automatically sets Status to 'Applied' if an Unplaced student submits an application
        cursor.execute("DROP TRIGGER IF EXISTS trg_after_application_insert")
        cursor.execute("""
            CREATE TRIGGER trg_after_application_insert
            AFTER INSERT ON Application
            FOR EACH ROW
            BEGIN
                UPDATE Student
                SET Status = 'Applied'
                WHERE Student_ID = NEW.Student_ID AND Status = 'Unplaced';
            END
        """)

        # Step 4: PL/SQL Concept 2 - STORED PROCEDURE WITH CURSOR
        # Uses a CURSOR to iterate through all students and calculate placement statistics
        cursor.execute("DROP PROCEDURE IF EXISTS sp_get_placement_summary")
        cursor.execute("""
            CREATE PROCEDURE sp_get_placement_summary()
            BEGIN
                DECLARE done INT DEFAULT FALSE;
                DECLARE v_status VARCHAR(30);
                DECLARE v_cgpa DECIMAL(4,2);
                
                DECLARE total_students INT DEFAULT 0;
                DECLARE placed_students INT DEFAULT 0;
                DECLARE cgpa_sum DECIMAL(10,2) DEFAULT 0.00;

                -- Declare Cursor
                DECLARE student_cursor CURSOR FOR 
                    SELECT Status, CGPA FROM Student;
                DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

                OPEN student_cursor;

                read_loop: LOOP
                    FETCH student_cursor INTO v_status, v_cgpa;
                    IF done THEN
                        LEAVE read_loop;
                    END IF;

                    SET total_students = total_students + 1;
                    SET cgpa_sum = cgpa_sum + v_cgpa;

                    IF v_status = 'Placed' THEN
                        SET placed_students = placed_students + 1;
                    END IF;
                END LOOP;

                CLOSE student_cursor;

                SELECT 
                    total_students AS Total_Students,
                    placed_students AS Placed_Students,
                    (total_students - placed_students) AS Unplaced_Or_Pending,
                    IF(total_students > 0, ROUND((placed_students / total_students) * 100, 2), 0) AS Placement_Percentage,
                    IF(total_students > 0, ROUND(cgpa_sum / total_students, 2), 0) AS Average_CGPA;
            END
        """)

        conn.commit()
        return {"success": True, "message": "Database, tables, trigger, and procedure initialized successfully."}

    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


# ==========================================
# 3. STUDENT DML OPERATIONS (CRUD)
# ==========================================
def add_student(student_id: int, name: str, branch: str, cgpa: float, status: str = "Unplaced"):
    """INSERT a new student record."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor()
        query = """
            INSERT INTO Student (Student_ID, Name, Branch, CGPA, Status)
            VALUES (%s, %s, %s, %s, %s)
        """
        cursor.execute(query, (student_id, name, branch, cgpa, status))
        conn.commit()
        return {"success": True, "message": f"Student {name} (ID: {student_id}) added successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def get_all_students():
    """SELECT all students."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute("SELECT Student_ID, Name, Branch, CAST(CGPA AS DOUBLE) AS CGPA, Status FROM Student ORDER BY Student_ID")
        rows = cursor.fetchall()
        return {"success": True, "data": rows}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def get_student_by_id(student_id: int):
    """SELECT a single student by ID."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT Student_ID, Name, Branch, CAST(CGPA AS DOUBLE) AS CGPA, Status FROM Student WHERE Student_ID = %s",
            (student_id,)
        )
        row = cursor.fetchone()
        if not row:
            return {"success": False, "error": f"Student ID {student_id} not found."}
        return {"success": True, "data": row}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def update_student(student_id: int, name: str = None, branch: str = None, cgpa: float = None, status: str = None):
    """UPDATE student details or placement status dynamically."""
    conn = None
    cursor = None
    try:
        fields = []
        values = []
        if name is not None:
            fields.append("Name = %s")
            values.append(name)
        if branch is not None:
            fields.append("Branch = %s")
            values.append(branch)
        if cgpa is not None:
            fields.append("CGPA = %s")
            values.append(cgpa)
        if status is not None:
            fields.append("Status = %s")
            values.append(status)

        if not fields:
            return {"success": False, "error": "No fields provided to update."}

        values.append(student_id)
        query = f"UPDATE Student SET {', '.join(fields)} WHERE Student_ID = %s"

        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(query, tuple(values))
        conn.commit()

        if cursor.rowcount == 0:
            return {"success": False, "error": f"Student ID {student_id} not found or no changes made."}
        return {"success": True, "message": f"Student ID {student_id} updated successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def delete_student(student_id: int):
    """DELETE a student by ID (automatically cascades to Application)."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM Student WHERE Student_ID = %s", (student_id,))
        conn.commit()
        if cursor.rowcount == 0:
            return {"success": False, "error": f"Student ID {student_id} not found."}
        return {"success": True, "message": f"Student ID {student_id} deleted successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


# ==========================================
# 4. COMPANY DML OPERATIONS (CRUD)
# ==========================================
def add_company(company_id: int, name: str, role: str, package: float):
    """INSERT a new company record."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor()
        query = """
            INSERT INTO Company (Company_ID, Name, Role, Package)
            VALUES (%s, %s, %s, %s)
        """
        cursor.execute(query, (company_id, name, role, package))
        conn.commit()
        return {"success": True, "message": f"Company {name} (ID: {company_id}) added successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def get_all_companies():
    """SELECT all companies."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute("SELECT Company_ID, Name, Role, CAST(Package AS DOUBLE) AS Package FROM Company ORDER BY Company_ID")
        rows = cursor.fetchall()
        return {"success": True, "data": rows}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def update_company(company_id: int, name: str = None, role: str = None, package: float = None):
    """UPDATE company details dynamically."""
    conn = None
    cursor = None
    try:
        fields = []
        values = []
        if name is not None:
            fields.append("Name = %s")
            values.append(name)
        if role is not None:
            fields.append("Role = %s")
            values.append(role)
        if package is not None:
            fields.append("Package = %s")
            values.append(package)

        if not fields:
            return {"success": False, "error": "No fields provided to update."}

        values.append(company_id)
        query = f"UPDATE Company SET {', '.join(fields)} WHERE Company_ID = %s"

        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(query, tuple(values))
        conn.commit()

        if cursor.rowcount == 0:
            return {"success": False, "error": f"Company ID {company_id} not found or no changes made."}
        return {"success": True, "message": f"Company ID {company_id} updated successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def delete_company(company_id: int):
    """DELETE a company by ID (automatically cascades to Application)."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM Company WHERE Company_ID = %s", (company_id,))
        conn.commit()
        if cursor.rowcount == 0:
            return {"success": False, "error": f"Company ID {company_id} not found."}
        return {"success": True, "message": f"Company ID {company_id} deleted successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


# ==========================================
# 5. APPLICATION & PLACEMENT OPERATIONS
# ==========================================
def create_application(student_id: int, company_id: int, app_id: int = None):
    """
    INSERT a student application for a company.
    Fires 'trg_after_application_insert' in MySQL to update Student Status if 'Unplaced'.
    """
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor()
        if app_id is not None:
            query = "INSERT INTO Application (App_ID, Student_ID, Company_ID) VALUES (%s, %s, %s)"
            cursor.execute(query, (app_id, student_id, company_id))
        else:
            query = "INSERT INTO Application (Student_ID, Company_ID) VALUES (%s, %s)"
            cursor.execute(query, (student_id, company_id))
        conn.commit()
        return {
            "success": True,
            "app_id": cursor.lastrowid,
            "message": f"Student {student_id} successfully applied to Company {company_id}."
        }
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def get_all_applications():
    """SELECT all applications joined with Student and Company details."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        query = """
            SELECT 
                a.App_ID,
                s.Student_ID,
                s.Name AS Student_Name,
                s.Branch,
                CAST(s.CGPA AS DOUBLE) AS CGPA,
                s.Status AS Student_Status,
                c.Company_ID,
                c.Name AS Company_Name,
                c.Role,
                CAST(c.Package AS DOUBLE) AS Package
            FROM Application a
            INNER JOIN Student s ON a.Student_ID = s.Student_ID
            INNER JOIN Company c ON a.Company_ID = c.Company_ID
            ORDER BY a.App_ID DESC
        """
        cursor.execute(query)
        rows = cursor.fetchall()
        return {"success": True, "data": rows}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def delete_application(app_id: int):
    """DELETE an application by App_ID."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM Application WHERE App_ID = %s", (app_id,))
        conn.commit()
        if cursor.rowcount == 0:
            return {"success": False, "error": f"Application ID {app_id} not found."}
        return {"success": True, "message": f"Application ID {app_id} deleted successfully."}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def get_placement_analytics():
    """
    Calls the PL/SQL Stored Procedure 'sp_get_placement_summary' (which uses a CURSOR)
    to return overall placement statistics.
    """
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.callproc("sp_get_placement_summary")
        stats = {}
        for result in cursor.stored_results():
            stats = result.fetchone()
        # Convert Decimal objects if any to float for JSON compatibility
        if stats:
            for k, v in stats.items():
                if v is not None and not isinstance(v, (int, float, str)):
                    stats[k] = float(v)
        return {"success": True, "data": stats}
    except Error as e:
        return {"success": False, "error": str(e)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


# ==========================================
# 6. QUICK SELF-TEST (RUN DIRECTLY IN TERMINAL)
# ==========================================
if __name__ == "__main__":
    print("1. Initializing Database & PL/SQL Objects...")
    print(init_db())

    print("\n2. Inserting Sample Data...")
    print(add_student(101, "Aarav Sharma", "CSE", 8.75, "Unplaced"))
    print(add_student(102, "Priya Nair", "IT", 9.10, "Placed"))
    print(add_company(201, "TechCorp", "Software Engineer", 12.50))
    print(add_company(202, "DataSystems", "Data Analyst", 9.00))

    print("\n3. Submitting Application (Fires Trigger)...")
    print(create_application(student_id=101, company_id=201))

    print("\n4. Fetching Applications (Joined View)...")
    print(get_all_applications())

    print("\n5. Running Stored Procedure with Cursor for Result Calculation...")
    print(get_placement_analytics())