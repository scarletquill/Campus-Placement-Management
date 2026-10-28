"""
Campus Placement Management System - Flask REST API
Uses the existing CampusPlacementDB database and its eight tables.
This application does NOT create, drop, or modify the database schema.

Install:
    python -m pip install flask flask-cors mysql-connector-python

Before running:
    1. Set MYSQL_CONFIG["password"] to your MySQL root password.
    2. Ensure CampusPlacementDB and all eight tables already exist.
    3. Run: python campus_backend.py
    4. API base URL: http://127.0.0.1:5000/api
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import mysql.connector
from mysql.connector import Error

app = Flask(__name__)
CORS(app)  # Allows a separate frontend (for example React/Vite) to call this API.

MYSQL_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": "hehe",  # Replace with your actual MySQL password
    "database": "CampusPlacementDB",
    "port": 3306,
}

# Only these tables and columns can be accessed through the generic CRUD routes.
# The primary key for each table is listed separately.
TABLES = {
    "departments": {
        "sql": "Departments",
        "pk": "department_id",
        "columns": [
            "department_id", "department_name", "department_code"
        ],
        "required": ["department_name", "department_code"],
        "auto_pk": True,
    },
    "students": {
        "sql": "Students",
        "pk": "student_id",
        "columns": [
            "student_id", "full_name", "email", "phone", "department_id",
            "cgpa", "graduation_year", "placement_status"
        ],
        "required": [
            "full_name", "email", "department_id", "cgpa", "graduation_year"
        ],
        "auto_pk": True,
    },
    "companies": {
        "sql": "Companies",
        "pk": "company_id",
        "columns": ["company_id", "company_name", "industry", "contact_email"],
        "required": ["company_name", "industry", "contact_email"],
        "auto_pk": True,
    },
    "placement_drives": {
        "sql": "Placement_Drives",
        "pk": "drive_id",
        "columns": [
            "drive_id", "company_id", "drive_date", "academic_year",
            "venue", "drive_status"
        ],
        "required": ["company_id", "drive_date", "academic_year"],
        "auto_pk": True,
    },
    "job_postings": {
        "sql": "Job_Postings",
        "pk": "job_id",
        "columns": [
            "job_id", "company_id", "drive_id", "job_role", "package_lpa",
            "min_cgpa", "job_location", "application_deadline"
        ],
        "required": [
            "company_id", "job_role", "package_lpa", "min_cgpa",
            "application_deadline"
        ],
        "auto_pk": True,
    },
    "applications": {
        "sql": "Applications",
        "pk": "application_id",
        "columns": [
            "application_id", "student_id", "job_id", "application_date",
            "application_status"
        ],
        "required": ["student_id", "job_id"],
        "auto_pk": True,
    },
    "interview_rounds": {
        "sql": "Interview_Rounds",
        "pk": "interview_id",
        "columns": [
            "interview_id", "application_id", "round_name", "interview_date",
            "interview_mode", "result", "feedback"
        ],
        "required": ["application_id", "round_name"],
        "auto_pk": True,
    },
    "offers": {
        "sql": "Offers",
        "pk": "offer_id",
        "columns": [
            "offer_id", "application_id", "offer_date",
            "offered_package_lpa", "offer_status", "joining_date"
        ],
        "required": ["application_id", "offered_package_lpa"],
        "auto_pk": True,
    },
}


def get_connection():
    """Connect to the existing database."""
    return mysql.connector.connect(**MYSQL_CONFIG)


def json_error(message, status=400):
    return jsonify({"success": False, "error": str(message)}), status


def run_query(query, params=(), fetch=None, commit=False, callproc=None):
    """Run a parameterized query and return JSON-friendly data."""
    conn = None
    cursor = None
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        if callproc:
            cursor.callproc(callproc[0], callproc[1])
            rows = []
            for result in cursor.stored_results():
                rows.extend(result.fetchall())
            return {"success": True, "data": rows}
        cursor.execute(query, params)
        if fetch == "one":
            row = cursor.fetchone()
            return {"success": True, "data": row}
        if fetch == "all":
            return {"success": True, "data": cursor.fetchall()}
        if commit:
            conn.commit()
            return {
                "success": True,
                "message": "Operation completed successfully.",
                "inserted_id": cursor.lastrowid,
                "affected_rows": cursor.rowcount,
            }
        return {"success": True}
    except Error as exc:
        if conn and conn.is_connected():
            conn.rollback()
        return {"success": False, "error": str(exc)}
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()


def table_config(table_name):
    config = TABLES.get(table_name.lower())
    if not config:
        return None
    return config


@app.get("/")
def home():
    return jsonify({
        "success": True,
        "message": "Campus Placement Management API is running.",
        "api_base": "/api",
        "available_resources": list(TABLES.keys()),
    })


@app.get("/api/health")
def health():
    result = run_query("SELECT DATABASE() AS database_name", fetch="one")
    if result["success"]:
        result["message"] = "MySQL connection successful."
        return jsonify(result)
    return json_error(result.get("error", "Database connection failed."), 500)


@app.route("/api/<table_name>", methods=["GET", "POST"])
def collection_crud(table_name):
    """GET list / POST create for any of the eight approved tables."""
    cfg = table_config(table_name)
    if not cfg:
        return json_error("Unknown table/resource.", 404)

    table = cfg["sql"]
    pk = cfg["pk"]

    if request.method == "GET":
        result = run_query(f"SELECT * FROM `{table}` ORDER BY `{pk}` DESC", fetch="all")
        return (jsonify(result), 200) if result["success"] else json_error(result["error"], 500)

    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return json_error("Send a JSON object in the request body.")

    unknown = set(data) - set(cfg["columns"])
    if unknown:
        return json_error("Unknown column(s): " + ", ".join(sorted(unknown)))

    missing = [field for field in cfg["required"] if data.get(field) is None]
    if missing:
        return json_error("Required field(s) missing: " + ", ".join(missing))

    if cfg["auto_pk"] and data.get(pk) is None:
        data.pop(pk, None)

    if not data:
        return json_error("No fields supplied.")

    columns = list(data.keys())
    column_sql = ", ".join(f"`{column}`" for column in columns)
    placeholders = ", ".join(["%s"] * len(columns))
    query = f"INSERT INTO `{table}` ({column_sql}) VALUES ({placeholders})"
    result = run_query(query, tuple(data[column] for column in columns), commit=True)

    if not result["success"]:
        return json_error(result["error"], 400)

    result["id"] = result.pop("inserted_id", None)
    return jsonify(result), 201


@app.route("/api/<table_name>/<int:record_id>", methods=["GET", "PUT", "PATCH", "DELETE"])
def record_crud(table_name, record_id):
    """GET one / PUT or PATCH update / DELETE one record."""
    cfg = table_config(table_name)
    if not cfg:
        return json_error("Unknown table/resource.", 404)

    table = cfg["sql"]
    pk = cfg["pk"]

    if request.method == "GET":
        result = run_query(
            f"SELECT * FROM `{table}` WHERE `{pk}` = %s",
            (record_id,), fetch="one"
        )
        if not result["success"]:
            return json_error(result["error"], 500)
        if result["data"] is None:
            return json_error("Record not found.", 404)
        return jsonify(result)

    if request.method in ("PUT", "PATCH"):
        data = request.get_json(silent=True)
        if not isinstance(data, dict) or not data:
            return json_error("Send a non-empty JSON object.")

        unknown = set(data) - set(cfg["columns"])
        if unknown:
            return json_error("Unknown column(s): " + ", ".join(sorted(unknown)))
        # Primary keys are changed only by the database, not through updates.
        data.pop(pk, None)
        if not data:
            return json_error("No editable fields supplied.")

        set_sql = ", ".join(f"`{column}` = %s" for column in data)
        params = tuple(data.values()) + (record_id,)
        result = run_query(
            f"UPDATE `{table}` SET {set_sql} WHERE `{pk}` = %s",
            params, commit=True
        )
        if not result["success"]:
            return json_error(result["error"], 400)
        if result.get("affected_rows", 0) == 0:
            # Distinguish no-op update from missing row.
            check = run_query(
                f"SELECT `{pk}` FROM `{table}` WHERE `{pk}` = %s",
                (record_id,), fetch="one"
            )
            if check["success"] and check["data"] is None:
                return json_error("Record not found.", 404)
        return jsonify(result)

    result = run_query(
        f"DELETE FROM `{table}` WHERE `{pk}` = %s",
        (record_id,), commit=True
    )
    if not result["success"]:
        # Foreign-key restrictions may prevent deletion, by design.
        return json_error(result["error"], 409)
    if result.get("affected_rows", 0) == 0:
        return json_error("Record not found.", 404)
    return jsonify(result)


@app.get("/api/eligible-students/<int:job_id>")
def eligible_students(job_id):
    """Call the GetEligibleStudents stored procedure already in the database."""
    result = run_query(
        "", fetch=None,
        callproc=("GetEligibleStudents", (job_id,))
    )
    return (jsonify(result), 200) if result["success"] else json_error(result["error"], 500)


@app.get("/api/analytics/placement")
def placement_analytics():
    """Dashboard summary using the existing eight-table schema."""
    query = """
        SELECT
            (SELECT COUNT(*) FROM Students) AS total_students,
            (SELECT COUNT(DISTINCT a.student_id)
             FROM Applications a
             JOIN Offers o ON o.application_id = a.application_id
             WHERE o.offer_status = 'Accepted') AS placed_students,
            (SELECT COUNT(*) FROM Companies) AS total_companies,
            (SELECT COUNT(*) FROM Placement_Drives) AS total_drives,
            (SELECT COUNT(*) FROM Job_Postings) AS total_jobs,
            (SELECT COUNT(*) FROM Applications) AS total_applications,
            (SELECT ROUND(AVG(cgpa), 2) FROM Students) AS average_cgpa
    """
    result = run_query(query, fetch="one")
    return (jsonify(result), 200) if result["success"] else json_error(result["error"], 500)


if __name__ == "__main__":
    print("Starting Campus Placement Management API...")
    print("Database:", MYSQL_CONFIG["database"])
    print("API URL: http://127.0.0.1:5000")
    print("Health check: http://127.0.0.1:5000/api/health")
    app.run(host="127.0.0.1", port=5000, debug=True)
