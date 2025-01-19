package ghidrassist;

import ghidra.framework.preferences.Preferences;
import ghidra.program.model.address.Address;
import ghidra.util.Msg;
import java.sql.*;

public class AnalysisDB {
    private static final String DB_PATH_PROPERTY = "GhidrAssist.AnalysisDBPath";
    private static final String DEFAULT_DB_PATH = "ghidrassist_analysis.db";
    private Connection connection;

    public AnalysisDB() {
        String dbPath = Preferences.getProperty(DB_PATH_PROPERTY, DEFAULT_DB_PATH);
        initializeDatabase(dbPath);
    }

    private void initializeDatabase(String dbPath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createAnalysisTable();
        } catch (SQLException e) {
            Msg.showError(this, null, "Database Error", "Failed to initialize Analysis database: " + e.getMessage());
        }
    }

    private void createAnalysisTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS GHAnalysis ("
                + "program_name TEXT NOT NULL,"
                + "function_address TEXT NOT NULL,"
                + "query TEXT NOT NULL,"
                + "response TEXT NOT NULL,"
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (program_name, function_address)"
                + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    public void upsertAnalysis(String programName, Address functionAddress, String query, String response) {
        String upsertSQL = "INSERT INTO GHAnalysis (program_name, function_address, query, response) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(program_name, function_address) "
                + "DO UPDATE SET query = ?, response = ?, timestamp = CURRENT_TIMESTAMP";
        
        try (PreparedStatement pstmt = connection.prepareStatement(upsertSQL)) {
            pstmt.setString(1, programName);
            pstmt.setString(2, functionAddress.toString());
            pstmt.setString(3, query);
            pstmt.setString(4, response);
            pstmt.setString(5, query);
            pstmt.setString(6, response);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Msg.showError(this, null, "Database Error", "Failed to store analysis: " + e.getMessage());
        }
    }

    public Analysis getAnalysis(String programName, Address functionAddress) {
        String selectSQL = "SELECT query, response, timestamp FROM GHAnalysis "
                + "WHERE program_name = ? AND function_address = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            pstmt.setString(1, programName);
            pstmt.setString(2, functionAddress.toString());
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Analysis(
                    rs.getString("query"),
                    rs.getString("response"),
                    rs.getTimestamp("timestamp")
                );
            }
        } catch (SQLException e) {
            Msg.showError(this, null, "Database Error", "Failed to retrieve analysis: " + e.getMessage());
        }
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            Msg.showError(this, null, "Database Error", "Failed to close Analysis database connection: " + e.getMessage());
        }
    }

    public static class Analysis {
        private final String query;
        private final String response;
        private final Timestamp timestamp;

        public Analysis(String query, String response, Timestamp timestamp) {
            this.query = query;
            this.response = response;
            this.timestamp = timestamp;
        }

        public String getQuery() { return query; }
        public String getResponse() { return response; }
        public Timestamp getTimestamp() { return timestamp; }
    }
}