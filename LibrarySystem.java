import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// ======================= Database & Utils =======================
class Database {
    private static final String DB_URL = "jdbc:sqlite:library.db";
    private Connection conn;

    public Database() {
        try {
            conn = DriverManager.getConnection(DB_URL);
            System.out.println("Connected to SQLite.");
            createTables();
            seedDefaults();
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "DB Error: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        Statement st = conn.createStatement();
        st.execute("PRAGMA foreign_keys = ON");

        st.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "role TEXT NOT NULL," +
                "email TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL)");

        st.execute("CREATE TABLE IF NOT EXISTS books (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "author TEXT NOT NULL," +
                "category TEXT," +
                "year INTEGER," +
                "status TEXT DEFAULT 'available')"); // available, borrowed

        st.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "book_id INTEGER NOT NULL," +
                "borrow_date TEXT," +
                "due_date TEXT," +
                "return_date TEXT," +
                "fine REAL DEFAULT 0," +
                "fine_settled INTEGER DEFAULT 0," +
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE)");

        st.execute("CREATE TABLE IF NOT EXISTS reservations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "book_id INTEGER NOT NULL," +
                "reservation_date TEXT," +
                "status TEXT DEFAULT 'pending'," + // pending, completed, canceled
                "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE)");

        st.execute("CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT NOT NULL)");
    }

    private void seedDefaults() {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO settings(key,value) VALUES" +
                        "('borrow_days','14')," +
                        "('max_borrow','5')," +
                        "('fine_per_day','1')")) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}

        // حساب Admin افتراضي إن لم يوجد
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO users(id,name,role,email,password) VALUES(1,'Admin','Admin','admin@lib.local','admin')")) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public ResultSet query(String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i=0;i<params.length;i++) ps.setObject(i+1, params[i]);
        return ps.executeQuery();
    }

    public int update(String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i=0;i<params.length;i++) ps.setObject(i+1, params[i]);
        return ps.executeUpdate();
    }

    public String getSetting(String key, String def) {
        try (ResultSet rs = query("SELECT value FROM settings WHERE key=?", key)) {
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return def;
    }

    public void setSetting(String key, String value) {
        try {
            update("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", key, value);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Settings error: " + e.getMessage());
        }
    }

    public void backup() {
        try {
            File src = new File("library.db");
            File dst = new File("library_backup.db");
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(null, "Backup created: " + dst.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Backup failed: " + e.getMessage());
        }
    }
}

class UI {
    static void loadTable(JTable table, ResultSet rs) throws SQLException {
        DefaultTableModel model = new DefaultTableModel();
        int cols = rs.getMetaData().getColumnCount();
        for (int i=1;i<=cols;i++) model.addColumn(rs.getMetaData().getColumnLabel(i));
        while (rs.next()) {
            Object[] row = new Object[cols];
            for (int i=1;i<=cols;i++) row[i-1] = rs.getObject(i);
            model.addRow(row);
        }
        table.setModel(model);
    }

    static JPanel labeledField(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(6,6));
        p.add(new JLabel(label), BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    static int getIntField(JTextField t, int def) {
        try { return Integer.parseInt(t.getText().trim()); } catch(Exception e){ return def; }
    }
}

// ======================= Student Forms =======================
class SearchBooksForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();
    private final JTextField q = new JTextField();

    public SearchBooksForm(Database db) {
        this.db = db;
        setTitle("Search Books");
        setSize(800, 450);
        setLocationRelativeTo(null);

        JButton search = new JButton("Search");
        search.addActionListener(e -> search());
        JButton all = new JButton("All");
        all.addActionListener(e -> loadAll());

        JPanel top = new JPanel(new BorderLayout(6,6));
        top.add(q, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(search); btns.add(all);
        top.add(btns, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        loadAll();
    }

    private void search() {
        String s = "%" + q.getText().trim() + "%";
        try (ResultSet rs = db.query(
                "SELECT id,title,author,category,year,status FROM books " +
                "WHERE title LIKE ? OR author LIKE ? OR category LIKE ? ORDER BY title", s,s,s)) {
            UI.loadTable(table, rs);
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private void loadAll() {
        try (ResultSet rs = db.query("SELECT id,title,author,category,year,status FROM books ORDER BY title")) {
            UI.loadTable(table, rs);
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }
}

class BorrowBookForm extends JFrame {
    private final Database db;
    private final JTextField tfUser = new JTextField();
    private final JTextField tfBook = new JTextField();

    public BorrowBookForm(Database db) {
        this.db = db;
        setTitle("Borrow Book");
        setSize(380, 200);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(0,1,6,6));

        add(UI.labeledField("User ID:", tfUser));
        add(UI.labeledField("Book ID:", tfBook));

        JButton borrow = new JButton("Borrow");
        borrow.addActionListener(e -> doBorrow());
        add(borrow);
    }

    private void doBorrow() {
        try {
            int userId = Integer.parseInt(tfUser.getText().trim());
            int bookId = Integer.parseInt(tfBook.getText().trim());

            // تحقق من الحد الأقصى للاستعارة
            int maxBorrow = Integer.parseInt(db.getSetting("max_borrow","5"));
            try (ResultSet rs = db.query("SELECT COUNT(*) FROM transactions WHERE user_id=? AND return_date IS NULL", userId)) {
                if (rs.next() && rs.getInt(1) >= maxBorrow) {
                    JOptionPane.showMessageDialog(this, "Reached max borrow limit ("+maxBorrow+").");
                    return;
                }
            }

            // تحقق حالة الكتاب
            try (ResultSet rs = db.query("SELECT status FROM books WHERE id=?", bookId)) {
                if (!rs.next()) { JOptionPane.showMessageDialog(this, "Book not found."); return; }
                if (!"available".equalsIgnoreCase(rs.getString(1))) {
                    JOptionPane.showMessageDialog(this, "Book is not available.");
                    return;
                }
            }

            int days = Integer.parseInt(db.getSetting("borrow_days","14"));
            LocalDate b = LocalDate.now();
            LocalDate d = b.plusDays(days);

            db.update("INSERT INTO transactions(user_id,book_id,borrow_date,due_date) VALUES(?,?,?,?)",
                    userId, bookId, b.toString(), d.toString());
            db.update("UPDATE books SET status='borrowed' WHERE id=?", bookId);

            JOptionPane.showMessageDialog(this, "Borrowed. Due: " + d);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: "+ex.getMessage()); }
    }
}

class ReturnBookForm extends JFrame {
    private final Database db;
    private final JTextField tfTrans = new JTextField();

    public ReturnBookForm(Database db) {
        this.db = db;
        setTitle("Return Book");
        setSize(380, 160);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(0,1,6,6));

        add(UI.labeledField("Transaction ID:", tfTrans));
        JButton ret = new JButton("Return");
        ret.addActionListener(e -> doReturn());
        add(ret);
    }

    private void doReturn() {
        try {
            int tid = Integer.parseInt(tfTrans.getText().trim());
            ResultSet rs = db.query("SELECT book_id,due_date,return_date FROM transactions WHERE id=?", tid);
            if (!rs.next()) { JOptionPane.showMessageDialog(this, "Transaction not found."); return; }
            if (rs.getString("return_date") != null) { JOptionPane.showMessageDialog(this, "Already returned."); return; }

            int bookId = rs.getInt("book_id");
            LocalDate due = LocalDate.parse(rs.getString("due_date"));
            LocalDate retDate = LocalDate.now();
            long late = Math.max(0, ChronoUnit.DAYS.between(due, retDate));
            double finePerDay = Double.parseDouble(db.getSetting("fine_per_day","1"));
            double fine = late * finePerDay;

            db.update("UPDATE transactions SET return_date=?, fine=? WHERE id=?", retDate.toString(), fine, tid);
            db.update("UPDATE books SET status='available' WHERE id=?", bookId);

            JOptionPane.showMessageDialog(this, "Returned. Fine: " + fine);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: "+ex.getMessage()); }
    }
}

class ReserveBookForm extends JFrame {
    private final Database db;
    private final JTextField tfUser = new JTextField();
    private final JTextField tfBook = new JTextField();

    public ReserveBookForm(Database db) {
        this.db = db;
        setTitle("Reserve Book");
        setSize(380, 200);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(0,1,6,6));

        add(UI.labeledField("User ID:", tfUser));
        add(UI.labeledField("Book ID:", tfBook));

        JButton reserve = new JButton("Reserve");
        reserve.addActionListener(e -> doReserve());
        add(reserve);
    }

    private void doReserve() {
        try {
            int userId = Integer.parseInt(tfUser.getText().trim());
            int bookId = Integer.parseInt(tfBook.getText().trim());
            db.update("INSERT INTO reservations(user_id,book_id,reservation_date,status) VALUES(?,?,?,?)",
                    userId, bookId, LocalDate.now().toString(), "pending");
            JOptionPane.showMessageDialog(this, "Reserved.");
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: "+ex.getMessage()); }
    }
}

// ======================= Librarian Forms =======================
class ManageBooksForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();
    private final JTextField tTitle = new JTextField(), tAuthor = new JTextField(),
            tCat = new JTextField(), tYear = new JTextField();

    public ManageBooksForm(Database db) {
        this.db = db;
        setTitle("Manage Books");
        setSize(900, 500);
        setLocationRelativeTo(null);

        JPanel form = new JPanel(new GridLayout(0,1,6,6));
        form.add(UI.labeledField("Title:", tTitle));
        form.add(UI.labeledField("Author:", tAuthor));
        form.add(UI.labeledField("Category:", tCat));
        form.add(UI.labeledField("Year:", tYear));

        JButton add = new JButton("Add");
        add.addActionListener(e -> addBook());
        JButton update = new JButton("Update (by selected row)");
        update.addActionListener(e -> updateBook());
        JButton del = new JButton("Delete (by selected row)");
        del.addActionListener(e -> deleteBook());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(add); actions.add(update); actions.add(del);

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int r = table.getSelectedRow();
                if (r>=0) {
                    tTitle.setText(String.valueOf(table.getValueAt(r,1)));
                    tAuthor.setText(String.valueOf(table.getValueAt(r,2)));
                    tCat.setText(String.valueOf(table.getValueAt(r,3)));
                    tYear.setText(String.valueOf(table.getValueAt(r,4)));
                }
            }
        });

        refresh();
    }

    private void refresh() {
        try (ResultSet rs = db.query("SELECT id,title,author,category,year,status FROM books ORDER BY id DESC")) {
            UI.loadTable(table, rs);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void addBook() {
        try {
            Integer yr = tYear.getText().trim().isEmpty()? null : Integer.parseInt(tYear.getText().trim());
            db.update("INSERT INTO books(title,author,category,year,status) VALUES(?,?,?,?, 'available')",
                    tTitle.getText().trim(), tAuthor.getText().trim(), tCat.getText().trim(), yr);
            refresh();
            clear();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error: "+e.getMessage()); }
    }

    private void updateBook() {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Select a row first."); return; }
        try {
            int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
            Integer yr = tYear.getText().trim().isEmpty()? null : Integer.parseInt(tYear.getText().trim());
            db.update("UPDATE books SET title=?, author=?, category=?, year=? WHERE id=?",
                    tTitle.getText().trim(), tAuthor.getText().trim(), tCat.getText().trim(), yr, id);
            refresh();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error: "+e.getMessage()); }
    }

    private void deleteBook() {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Select a row first."); return; }
        try {
            int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
            db.update("DELETE FROM books WHERE id=?", id);
            refresh(); clear();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error: "+e.getMessage()); }
    }

    private void clear() {
        tTitle.setText(""); tAuthor.setText(""); tCat.setText(""); tYear.setText("");
    }
}

class BorrowReturnManageForm extends JFrame {
    private final Database db;
    private final JTable tblAvailable = new JTable();
    private final JTable tblActive = new JTable();
    private final JTextField tfUser = new JTextField();

    public BorrowReturnManageForm(Database db) {
        this.db = db;
        setTitle("Borrow / Return Management");
        setSize(1000, 560);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new GridLayout(0,1,6,6));
        top.add(UI.labeledField("User ID (for borrowing):", tfUser));

        JButton borrow = new JButton("Borrow selected book");
        borrow.addActionListener(e -> doBorrowSelected());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadData());

        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topBtns.add(refresh); topBtns.add(borrow);
        top.add(topBtns);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tblAvailable), new JScrollPane(tblActive));
        split.setResizeWeight(0.5);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        JButton returnBtn = new JButton("Return selected active transaction");
        returnBtn.addActionListener(e -> doReturnSelected());
        add(returnBtn, BorderLayout.SOUTH);

        loadData();
    }

    private void loadData() {
        try (ResultSet rsA = db.query(
                "SELECT id,title,author,category,year FROM books WHERE status='available' ORDER BY title");
             ResultSet rsT = db.query(
                     "SELECT t.id,u.name as user, b.title as book, t.borrow_date, t.due_date " +
                             "FROM transactions t JOIN users u ON t.user_id=u.id JOIN books b ON t.book_id=b.id " +
                             "WHERE t.return_date IS NULL ORDER BY t.due_date")) {
            UI.loadTable(tblAvailable, rsA);
            UI.loadTable(tblActive, rsT);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void doBorrowSelected() {
        int r = tblAvailable.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this,"Select a book."); return; }
        int bookId = Integer.parseInt(String.valueOf(tblAvailable.getValueAt(r,0)));
        int userId;
        try { userId = Integer.parseInt(tfUser.getText().trim()); }
        catch(Exception ex){ JOptionPane.showMessageDialog(this,"Invalid user id."); return; }

        try {
            int maxBorrow = Integer.parseInt(db.getSetting("max_borrow","5"));
            try (ResultSet rs = db.query("SELECT COUNT(*) FROM transactions WHERE user_id=? AND return_date IS NULL", userId)) {
                if (rs.next() && rs.getInt(1) >= maxBorrow) {
                    JOptionPane.showMessageDialog(this, "Reached max borrow limit ("+maxBorrow+").");
                    return;
                }
            }
            int days = Integer.parseInt(db.getSetting("borrow_days","14"));
            LocalDate b = LocalDate.now();
            LocalDate d = b.plusDays(days);
            db.update("INSERT INTO transactions(user_id,book_id,borrow_date,due_date) VALUES(?,?,?,?)", userId, bookId, b.toString(), d.toString());
            db.update("UPDATE books SET status='borrowed' WHERE id=?", bookId);
            loadData();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void doReturnSelected() {
        int r = tblActive.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this,"Select a transaction."); return; }
        int tid = Integer.parseInt(String.valueOf(tblActive.getValueAt(r,0)));
        try (ResultSet rs = db.query("SELECT book_id,due_date FROM transactions WHERE id=?", tid)) {
            if (!rs.next()) { JOptionPane.showMessageDialog(this,"Not found."); return; }
            int bookId = rs.getInt(1);
            LocalDate due = LocalDate.parse(rs.getString(2));
            LocalDate ret = LocalDate.now();
            double finePerDay = Double.parseDouble(db.getSetting("fine_per_day","1"));
            long late = Math.max(0, ChronoUnit.DAYS.between(due, ret));
            double fine = late * finePerDay;

            db.update("UPDATE transactions SET return_date=?, fine=? WHERE id=?", ret.toString(), fine, tid);
            db.update("UPDATE books SET status='available' WHERE id=?", bookId);
            loadData();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
}

class ManageReservationsForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();

    public ManageReservationsForm(Database db) {
        this.db = db;
        setTitle("Manage Reservations");
        setSize(900, 500);
        setLocationRelativeTo(null);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        JButton setCompleted = new JButton("Mark as completed");
        setCompleted.addActionListener(e -> setStatus("completed"));
        JButton setCanceled = new JButton("Cancel");
        setCanceled.addActionListener(e -> setStatus("canceled"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(refresh); top.add(setCompleted); top.add(setCanceled);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        load();
    }

    private void load() {
        try (ResultSet rs = db.query(
                "SELECT r.id,u.name as user,b.title as book,r.reservation_date,r.status " +
                "FROM reservations r JOIN users u ON r.user_id=u.id JOIN books b ON r.book_id=b.id " +
                "ORDER BY r.id DESC")) {
            UI.loadTable(table, rs);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void setStatus(String status) {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this,"Select a row."); return; }
        int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
        try {
            db.update("UPDATE reservations SET status=? WHERE id=?", status, id);
            load();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
}

class ManageFinesForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();

    public ManageFinesForm(Database db) {
        this.db = db;
        setTitle("Manage Fines");
        setSize(900, 500);
        setLocationRelativeTo(null);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        JButton settle = new JButton("Mark Fine as Settled");
        settle.addActionListener(e -> settleFine());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(refresh); top.add(settle);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        load();
    }

    private void load() {
        try (ResultSet rs = db.query(
                "SELECT t.id, u.name as user, b.title as book, t.due_date, t.return_date, t.fine, t.fine_settled " +
                "FROM transactions t JOIN users u ON t.user_id=u.id JOIN books b ON t.book_id=b.id " +
                "WHERE t.return_date IS NOT NULL AND t.fine > 0 ORDER BY t.id DESC")) {
            UI.loadTable(table, rs);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void settleFine() {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Select a row."); return; }
        int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
        try {
            db.update("UPDATE transactions SET fine_settled=1 WHERE id=?", id);
            load();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
}

class ReportsForm extends JFrame {
    private final Database db;
    private final JLabel lBooks = new JLabel();
    private final JLabel lBorrowed = new JLabel();
    private final JLabel lUsers = new JLabel();
    private final JLabel lFines = new JLabel();

    public ReportsForm(Database db) {
        this.db = db;
        setTitle("Reports");
        setSize(420, 260);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(0,1,6,6));

        add(lBooks);
        add(lBorrowed);
        add(lUsers);
        add(lFines);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        add(refresh);

        load();
    }

    private void load() {
        try (ResultSet a = db.query("SELECT COUNT(*) FROM books");
             ResultSet b = db.query("SELECT COUNT(*) FROM books WHERE status='borrowed'");
             ResultSet c = db.query("SELECT COUNT(*) FROM users");
             ResultSet d = db.query("SELECT IFNULL(SUM(fine),0) FROM transactions WHERE fine>0 AND fine_settled=0")) {
            a.next(); b.next(); c.next(); d.next();
            lBooks.setText("Total books: " + a.getInt(1));
            lBorrowed.setText("Borrowed now: " + b.getInt(1));
            lUsers.setText("Total users: " + c.getInt(1));
            lFines.setText("Unsettled fines sum: " + d.getDouble(1));
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
}

// ======================= Admin Forms =======================
class ManageUsersForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();
    private final JTextField tName = new JTextField(), tEmail = new JTextField(),
            tRole = new JTextField("Student"), tPass = new JTextField();

    public ManageUsersForm(Database db) {
        this.db = db;
        setTitle("Manage Users");
        setSize(900, 520);
        setLocationRelativeTo(null);

        JPanel form = new JPanel(new GridLayout(0,1,6,6));
        form.add(UI.labeledField("Name:", tName));
        form.add(UI.labeledField("Email:", tEmail));
        form.add(UI.labeledField("Role (Student/Librarian/Admin):", tRole));
        form.add(UI.labeledField("Password:", tPass));

        JButton add = new JButton("Add");
        add.addActionListener(e -> addUser());
        JButton update = new JButton("Update (selected)");
        update.addActionListener(e -> updateUser());
        JButton del = new JButton("Delete (selected)");
        del.addActionListener(e -> deleteUser());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(add); actions.add(update); actions.add(del);

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int r = table.getSelectedRow();
                if (r>=0) {
                    tName.setText(String.valueOf(table.getValueAt(r,1)));
                    tRole.setText(String.valueOf(table.getValueAt(r,2)));
                    tEmail.setText(String.valueOf(table.getValueAt(r,3)));
                    tPass.setText(String.valueOf(table.getValueAt(r,4)));
                }
            }
        });

        refresh();
    }

    private void refresh() {
        try (ResultSet rs = db.query("SELECT id,name,role,email,password FROM users ORDER BY id DESC")) {
            UI.loadTable(table, rs);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void addUser() {
        try {
            db.update("INSERT INTO users(name,role,email,password) VALUES(?,?,?,?)",
                    tName.getText().trim(), tRole.getText().trim(), tEmail.getText().trim(), tPass.getText().trim());
            refresh(); clear();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void updateUser() {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Select a row."); return; }
        try {
            int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
            db.update("UPDATE users SET name=?, role=?, email=?, password=? WHERE id=?",
                    tName.getText().trim(), tRole.getText().trim(), tEmail.getText().trim(), tPass.getText().trim(), id);
            refresh();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void deleteUser() {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Select a row."); return; }
        try {
            int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
            db.update("DELETE FROM users WHERE id=?", id);
            refresh(); clear();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void clear() { tName.setText(""); tEmail.setText(""); tPass.setText(""); tRole.setText("Student"); }
}

class ManageRolesForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();

    public ManageRolesForm(Database db) {
        this.db = db;
        setTitle("Manage Roles");
        setSize(700, 460);
        setLocationRelativeTo(null);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());
        JButton setStudent = new JButton("Set Student");
        setStudent.addActionListener(e -> setRole("Student"));
        JButton setLibrarian = new JButton("Set Librarian");
        setLibrarian.addActionListener(e -> setRole("Librarian"));
        JButton setAdmin = new JButton("Set Admin");
        setAdmin.addActionListener(e -> setRole("Admin"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(refresh); top.add(setStudent); top.add(setLibrarian); top.add(setAdmin);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        load();
    }

    private void load() {
        try (ResultSet rs = db.query("SELECT id,name,role,email FROM users ORDER BY id DESC")) {
            UI.loadTable(table, rs);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void setRole(String role) {
        int r = table.getSelectedRow();
        if (r<0) { JOptionPane.showMessageDialog(this, "Select a user."); return; }
        int id = Integer.parseInt(String.valueOf(table.getValueAt(r,0)));
        try {
            db.update("UPDATE users SET role=? WHERE id=?", role, id);
            load();
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
}

class SettingsForm extends JFrame {
    private final Database db;
    private final JTextField tBorrowDays = new JTextField();
    private final JTextField tMaxBorrow = new JTextField();
    private final JTextField tFinePerDay = new JTextField();

    public SettingsForm(Database db) {
        this.db = db;
        setTitle("System Settings");
        setSize(420, 220);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(0,1,6,6));

        tBorrowDays.setText(db.getSetting("borrow_days","14"));
        tMaxBorrow.setText(db.getSetting("max_borrow","5"));
        tFinePerDay.setText(db.getSetting("fine_per_day","1"));

        add(UI.labeledField("Borrow Days:", tBorrowDays));
        add(UI.labeledField("Max Borrow:", tMaxBorrow));
        add(UI.labeledField("Fine per Day:", tFinePerDay));

        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            db.setSetting("borrow_days", tBorrowDays.getText().trim());
            db.setSetting("max_borrow", tMaxBorrow.getText().trim());
            db.setSetting("fine_per_day", tFinePerDay.getText().trim());
            JOptionPane.showMessageDialog(this, "Saved.");
        });
        add(save);
    }
}

class SystemReportsForm extends JFrame {
    private final Database db;
    private final JTable table = new JTable();

    public SystemReportsForm(Database db) {
        this.db = db;
        setTitle("System Reports (Transactions)");
        setSize(1000, 560);
        setLocationRelativeTo(null);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> load());

        add(refresh, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        load();
    }

    private void load() {
        try (ResultSet rs = db.query(
                "SELECT t.id, u.name as user, b.title as book, t.borrow_date, t.due_date, t.return_date, t.fine, t.fine_settled " +
                        "FROM transactions t " +
                        "JOIN users u ON t.user_id=u.id " +
                        "JOIN books b ON t.book_id=b.id " +
                        "ORDER BY t.id DESC")) {
            UI.loadTable(table, rs);
        } catch (SQLException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }
}

// ======================= Dashboards =======================
class StudentDashboard extends JFrame {
    public StudentDashboard(Database db) {
        setTitle("Student Dashboard");
        setSize(420, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridLayout(0,1,8,8));
        JButton b1 = new JButton("Search Books");
        b1.addActionListener(e -> new SearchBooksForm(db).setVisible(true));
        JButton b2 = new JButton("Borrow Book");
        b2.addActionListener(e -> new BorrowBookForm(db).setVisible(true));
        JButton b3 = new JButton("Return Book");
        b3.addActionListener(e -> new ReturnBookForm(db).setVisible(true));
        JButton b4 = new JButton("Reserve Book");
        b4.addActionListener(e -> new ReserveBookForm(db).setVisible(true));
        p.add(b1); p.add(b2); p.add(b3); p.add(b4);
        add(p);
    }
}

class LibrarianDashboard extends JFrame {
    public LibrarianDashboard(Database db) {
        setTitle("Librarian Dashboard");
        setSize(420, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridLayout(0,1,8,8));
        JButton b1 = new JButton("Manage Books");
        b1.addActionListener(e -> new ManageBooksForm(db).setVisible(true));
        JButton b2 = new JButton("Borrow/Return");
        b2.addActionListener(e -> new BorrowReturnManageForm(db).setVisible(true));
        JButton b3 = new JButton("Manage Reservations");
        b3.addActionListener(e -> new ManageReservationsForm(db).setVisible(true));
        JButton b4 = new JButton("Manage Fines");
        b4.addActionListener(e -> new ManageFinesForm(db).setVisible(true));
        JButton b5 = new JButton("Reports");
        b5.addActionListener(e -> new ReportsForm(db).setVisible(true));

        p.add(b1); p.add(b2); p.add(b3); p.add(b4); p.add(b5);
        add(p);
    }
}

class AdminDashboard extends JFrame {
    public AdminDashboard(Database db) {
        setTitle("Admin Dashboard");
        setSize(420, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridLayout(0,1,8,8));
        JButton b1 = new JButton("Manage Users");
        b1.addActionListener(e -> new ManageUsersForm(db).setVisible(true));
        JButton b2 = new JButton("Manage Roles");
        b2.addActionListener(e -> new ManageRolesForm(db).setVisible(true));
        JButton b3 = new JButton("Backup Database");
        b3.addActionListener(e -> db.backup());
        JButton b4 = new JButton("System Reports");
        b4.addActionListener(e -> new SystemReportsForm(db).setVisible(true));
        JButton b5 = new JButton("System Settings");
        b5.addActionListener(e -> new SettingsForm(db).setVisible(true));
        JButton b6 = new JButton("General Reports (KPIs)");
        b6.addActionListener(e -> new ReportsForm(db).setVisible(true));

        p.add(b1); p.add(b2); p.add(b3); p.add(b4); p.add(b5); p.add(b6);
        add(p);
    }
}

// ======================= Main =======================
public class LibrarySystem {
    public static void main(String[] args) {
        // تحسين شكل الواجهة الافتراضي
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored){}

        Database db = new Database();
        SwingUtilities.invokeLater(() -> {
            String[] roles = {"Student","Librarian","Admin"};
            String role = (String) JOptionPane.showInputDialog(null, "Select role:", "Login",
                    JOptionPane.QUESTION_MESSAGE, null, roles, roles[0]);
            if (role == null) return;

            switch (role) {
                case "Student": new StudentDashboard(db).setVisible(true); break;
                case "Librarian": new LibrarianDashboard(db).setVisible(true); break;
                case "Admin": new AdminDashboard(db).setVisible(true); break;
            }
        });
    }
}
