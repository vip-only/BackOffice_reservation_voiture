import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqlScriptRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: SqlScriptRunner <jdbcUrl> <user> <password> <sqlFile>");
            System.exit(1);
        }

        String jdbcUrl = args[0];
        String user = args[1];
        String password = args[2];
        Path sqlPath = Path.of(args[3]);

        String sql = Files.readString(sqlPath, StandardCharsets.UTF_8);
        List<String> statements = splitStatements(sql);

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(true);
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
                    trimmed = trimmed.substring(1).trim();
                }
                if (trimmed.isEmpty()) {
                    continue;
                }
                try (Statement s = conn.createStatement()) {
                    boolean hasResult = s.execute(trimmed);
                    if (hasResult) {
                        try (ResultSet rs = s.getResultSet()) {
                            ResultSetMetaData md = rs.getMetaData();
                            int cols = md.getColumnCount();
                            while (rs.next()) {
                                StringBuilder line = new StringBuilder();
                                for (int i = 1; i <= cols; i++) {
                                    if (i > 1) line.append(" | ");
                                    line.append(md.getColumnLabel(i)).append("=").append(rs.getString(i));
                                }
                                System.out.println(line);
                            }
                        }
                    }
                }
            }
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char n = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (!inSingleQuote && !inLineComment && c == '-' && n == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }

            if (c == '\'' ) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                continue;
            }

            if (!inSingleQuote && c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            statements.add(current.toString());
        }

        return statements;
    }
}
