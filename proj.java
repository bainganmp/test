/*
*  Project:  CSCI 5140 Phase 2
*  Author: Marvelyn Baingan
*  Date: October 17, 2025
*/


import java.sql.*;
import java.util.*;
import java.io;

public class proj {

    private static final String URL = "jdbc:mysql://localhost:3306/LPMedicalCenterDB";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "Marvelyn123";

    public static void main(String[] args) {
        // Validate arguments
        if (args.length == 0) {
            System.out.println("❗ Usage: ./proj <query_number> [parameter]");
            System.out.println("Example: ./proj 1 'Proc A'");
            return;
        }

        String queryNum = args[0].trim();
        String parameterValue = (args.length > 1)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "";

        String sql = getQueryByNumber(queryNum);
        if (sql == null) {
            System.out.println("❌ Invalid query number. Please use 1–8.");
            return;
        }

        if (requiresParameter(queryNum) && parameterValue.isEmpty()) {
            System.out.println("⚠️  This query requires a parameter. Please provide one after the query number.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            if (conn == null) {
                System.out.println("❌ Failed to establish database connection.");
                return;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                if (requiresParameter(queryNum)) {
                    pstmt.setString(1, parameterValue.trim());
                }

                try (ResultSet rs = pstmt.executeQuery()) {
                    displayResults(queryNum, rs);
                }

            } catch (SQLException e) {
                System.err.println("⚠️ SQL Execution Error: " + e.getMessage());
            }

        } catch (SQLException e) {
            System.err.println("❌ Database Connection Error: " + e.getMessage());
        }
    }

    // Maps query numbers to SQL
    private static String getQueryByNumber(String num) {
        return switch (num) {
            case "1" -> """
                SELECT Ph.physicianID, Ph.name, Ph.position, Ph.ssn
                FROM Physician Ph, Undergoes U, `Procedure` Pr
                WHERE Ph.physicianID = U.physicianID AND U.procedureID = Pr.ProcID
                AND Pr.name = ?; 
                """;
            case "2" -> """
                SELECT P.name AS patient_name, App_Phys.name AS physician_name, N.name AS nurse_name, 
                A.startDateTime AS start_datetime, A.endDateTime AS end_datetime, Pri_Phys.name AS primary_physician
                FROM Appointment A, Patient P, Nurse N, Physician Pri_Phys, Physician App_Phys
                WHERE A.patientID = P.patientID AND A.nurseID = N.nurseID AND P.primaryPhysID = Pri_Phys.physicianID 
                AND A.physicianID = App_Phys.physicianID AND A.physicianID <> P.primaryPhysID;
                """;
            case "3" -> """
                SELECT P.patientID, P.ssn, P.name, P.address, P.dob, P.phone, P.insurancenumber, P.primaryPhysID
                FROM Patient P, Undergoes U, `Procedure` Pr
                WHERE P.patientID = U.patientID AND U.procedureID = Pr.procID
                AND Pr.cost > ?;
                """;
            case "4" -> """
                SELECT P.PatientID, P.ssn, P.name, P.address, P.dob, P.phone, P.insurancenumber, P.primaryPhysID
                FROM Patient P, Department D
                WHERE P.primaryPhysID = D.headID 
                AND D.name = ?;
                """;
            case "5" -> """
                SELECT P.name AS patient_name, Ph.name AS physician_name, Pr.prescribeddate AS prescribed_date
                FROM Patient P, Physician Ph, Prescribes Pr, Medication M
                WHERE Pr.patientID = P.patientID AND Pr.physicianID = Ph.physicianID
                AND Pr.medicationID = M.medID 
                AND M.name = ?;
                """;
            case "6" -> """
                SELECT N.nurseID, N.name, N.position, N.ssn, O.startDate AS on_call_start_date, O.endDate AS on_call_end_date
                FROM Nurse N, Oncall O
                WHERE N.nurseID = O.nurseID AND ( ? BETWEEN O.startDate AND O.endDate);
                """;
            case "7" -> """
                SELECT S.roomID, P.name AS Patient, S.startDate AS Stay_Start_Date, S.endDate AS Stay_End_Date
                FROM Stay S, Patient P, Room R
                WHERE S.patientID = P.patientID AND S.roomID = R.roomID
                AND R.roomType = 'Double' AND ( ? BETWEEN S.startDate AND S.endDate)
                ORDER BY S.roomID;
                """;
            case "8" -> """
                SELECT Ap.patientID, P.ssn AS patient_ssn, P.name AS patient_name, P.address AS patient_address,
                P.dob AS patient_dob, P.phone AS patient_phone, P.insuranceNumber AS patientInsuranceNumber,
                P.primaryPhysID AS patient_primaryPhysID, Ap.physicianID AS patient_physicianID, Ph.name AS physician_name,
                Ph.position AS physician_position, Ph.ssn AS physician_ssn, Ap.appID AS appointmentID
                FROM Appointment Ap, Patient P, Physician Ph, AffiliatedWith Af, Department D
                WHERE P.patientID = Ap.patientID AND Ph.physicianID = Ap.physicianID 
                AND Ap.physicianID = Af.physicianID AND Af.departmentID = D.deptID 
                AND D.name = ?;
                """;
            default -> null;
        };
    }

    private static boolean requiresParameter(String queryNum) {
        return switch (queryNum) {
            case "1", "3", "4", "5", "6", "7", "8" -> true;
            default -> false;
        };
    }

    // Displays query results in table format
    private static void displayResults(String queryNum, ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        // Collect data
        List<String[]> rows = new ArrayList<>();
        String[] headers = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            headers[i] = meta.getColumnLabel(i + 1);
        }

        while (rs.next()) {
            String[] row = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                row[i] = Optional.ofNullable(rs.getString(i + 1)).orElse("NULL");
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            System.out.println("\n⚠️  No results found for this query.\n");
            return;
        }

        // Compute column widths
        int[] colWidths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            colWidths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < columnCount; i++) {
                colWidths[i] = Math.max(colWidths[i], row[i].length());
            }
        }

        // Print table
        printSeparator(colWidths);
        printRow(headers, colWidths);
        printSeparator(colWidths);
        for (String[] row : rows) {
            printRow(row, colWidths);
        }
        printSeparator(colWidths);
        System.out.println("\n✅ " + rows.size() + " row(s) displayed.\n");
    }

    private static void printSeparator(int[] widths) {
        for (int w : widths) {
            System.out.print("+");
            System.out.print("-".repeat(w + 2));
        }
        System.out.println("+");
    }

    private static void printRow(String[] row, int[] widths) {
        for (int i = 0; i < row.length; i++) {
            System.out.printf("| %-" + widths[i] + "s ", row[i]);
        }
        System.out.println("|");
    }
}
