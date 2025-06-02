import java.awt.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

public class PRWithAuth {

    // --- User Authentication ---
    static class User {
        String username, password, role;
        public User(String u, String p, String r) {
            username = u; password = p; role = r;
        }
    }

    private static Map<String, User> users = new HashMap<>();

    // --- Reservation ---
    static class Reservation {
        String name;
        String vehicleNumber;
        String parkingSpot;
        LocalDateTime dateTime;

        public Reservation(String n, String v, String p, LocalDateTime dt) {
            name = n; vehicleNumber = v; parkingSpot = p; dateTime = dt;
        }

        public Object[] toRow() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return new Object[]{name, vehicleNumber, parkingSpot, dateTime.format(formatter)};
        }
    }

    private static final String FILE_NAME = "reservations.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // Fully qualify List and ArrayList here to avoid ambiguity error
    private static java.util.List<Reservation> reservations = new java.util.ArrayList<>();

    // Swing components
    private static JFrame frame;
    private static JTextField txtName, txtVehicle, txtSpot, txtSearch;
    private static JButton btnReserve, btnClear, btnSearch, btnCancel, btnLogout;
    private static JTable table;
    private static DefaultTableModel tableModel;
    private static JLabel lblTotalCount;
    private static JSpinner dateFilterSpinner;

    private static User loggedInUser;

    public static void main(String[] args) {
        // Setup test users
        users.put("admin", new User("admin", "admin123", "admin"));
        users.put("user1", new User("user1", "user123", "user"));

        SwingUtilities.invokeLater(PRWithAuth::showLoginScreen);
    }

    private static void showLoginScreen() {
        JFrame loginFrame = new JFrame("Login - Parking Reservation");
        loginFrame.setSize(300, 180);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new GridLayout(4, 2, 5, 5));

        JTextField txtUser = new JTextField();
        JPasswordField txtPass = new JPasswordField();
        JButton btnLogin = new JButton("Login");
        JLabel lblStatus = new JLabel("", SwingConstants.CENTER);
        lblStatus.setForeground(Color.RED);

        loginFrame.add(new JLabel("Username:"));
        loginFrame.add(txtUser);
        loginFrame.add(new JLabel("Password:"));
        loginFrame.add(txtPass);
        loginFrame.add(new JLabel());
        loginFrame.add(btnLogin);
        loginFrame.add(lblStatus);

        btnLogin.addActionListener(e -> {
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword());
            if (users.containsKey(u) && users.get(u).password.equals(p)) {
                loggedInUser = users.get(u);
                loginFrame.dispose();
                showMainUI();
            } else {
                lblStatus.setText("Invalid username or password!");
            }
        });

        loginFrame.setVisible(true);
    }

    private static void showMainUI() {
        frame = new JFrame("Parking Reservation System - Logged in as: " + loggedInUser.username + " (" + loggedInUser.role + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 550);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setLocationRelativeTo(null);

        // Top input panel
        JPanel inputPanel = new JPanel(new GridLayout(2, 7, 8, 8));
        inputPanel.setBorder(new EmptyBorder(10, 10, 0, 10));

        inputPanel.add(new JLabel("Name:"));
        txtName = new JTextField();
        inputPanel.add(txtName);

        inputPanel.add(new JLabel("Vehicle Number:"));
        txtVehicle = new JTextField();
        inputPanel.add(txtVehicle);

        inputPanel.add(new JLabel("Parking Spot:"));
        txtSpot = new JTextField();
        inputPanel.add(txtSpot);

        btnReserve = new JButton("Reserve Spot");
        inputPanel.add(btnReserve);

        btnClear = new JButton("Clear All Reservations");
        inputPanel.add(btnClear);

        // Search panel
        inputPanel.add(new JLabel("Search (Name or Vehicle):"));
        txtSearch = new JTextField();
        inputPanel.add(txtSearch);

        btnSearch = new JButton("Search");
        inputPanel.add(btnSearch);

        btnCancel = new JButton("Cancel Selected");
        inputPanel.add(btnCancel);

        btnLogout = new JButton("Logout");
        inputPanel.add(btnLogout);

        inputPanel.add(new JLabel("Filter by Date:"));
        dateFilterSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateFilterSpinner, "yyyy-MM-dd");
        dateFilterSpinner.setEditor(dateEditor);
        inputPanel.add(dateFilterSpinner);

        JButton btnFilterDate = new JButton("Apply Date Filter");
        inputPanel.add(btnFilterDate);

        lblTotalCount = new JLabel("Total Reservations: 0");
        lblTotalCount.setHorizontalAlignment(SwingConstants.CENTER);
        frame.add(lblTotalCount, BorderLayout.SOUTH);

        // Table setup
        String[] columns = {"Name", "Vehicle Number", "Parking Spot", "Reservation Time"};
        tableModel = new DefaultTableModel(columns, 0) {
            // Disable cell editing
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        loadReservationsFromFile();
        updateTable(null, null);

        // Button actions
        btnReserve.addActionListener(e -> reserveSpot());
        btnClear.addActionListener(e -> clearAllReservations());
        btnSearch.addActionListener(e -> searchReservations());
        btnCancel.addActionListener(e -> cancelSelectedReservation());
        btnLogout.addActionListener(e -> logout());
        btnFilterDate.addActionListener(e -> filterByDate());

        // Role based access
        if (!loggedInUser.role.equalsIgnoreCase("admin")) {
            btnClear.setEnabled(false);
            btnCancel.setEnabled(false);
        }

        frame.setVisible(true);
    }

    private static void reserveSpot() {
        String name = txtName.getText().trim();
        String vehicle = txtVehicle.getText().trim();
        String spot = txtSpot.getText().trim();
        LocalDateTime now = LocalDateTime.now();

        if (name.isEmpty() || vehicle.isEmpty() || spot.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please fill in all fields.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check duplicate parking spot
        for (Reservation r : reservations) {
            if (r.parkingSpot.equalsIgnoreCase(spot)) {
                JOptionPane.showMessageDialog(frame, "Parking spot " + spot + " is already reserved.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (r.vehicleNumber.equalsIgnoreCase(vehicle)) {
                JOptionPane.showMessageDialog(frame, "Vehicle number " + vehicle + " already has a reservation.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        Reservation newRes = new Reservation(name, vehicle, spot, now);
        reservations.add(newRes);
        saveReservationsToFile();

        clearInputFields();

        JOptionPane.showMessageDialog(frame, "Reservation made for " + name + " at spot " + spot);

        updateTable(null, null);
    }

    private static void clearInputFields() {
        txtName.setText("");
        txtVehicle.setText("");
        txtSpot.setText("");
    }

    private static void clearAllReservations() {
        if (loggedInUser.role.equalsIgnoreCase("admin")) {
            int confirm = JOptionPane.showConfirmDialog(frame, "Are you sure you want to clear ALL reservations?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                reservations.clear();
                saveReservationsToFile();
                updateTable(null, null);
                JOptionPane.showMessageDialog(frame, "All reservations cleared.");
            }
        }
    }

    private static void searchReservations() {
        String query = txtSearch.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            updateTable(null, null);
            return;
        }
        updateTable((r) -> r.name.toLowerCase().contains(query) || r.vehicleNumber.toLowerCase().contains(query), null);
    }

    private static void filterByDate() {
        Date selected = (Date) dateFilterSpinner.getValue();
        LocalDate filterDate = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        updateTable(null, filterDate);
    }

    // Update table with optional filter and date filter
    private static void updateTable(java.util.function.Predicate<Reservation> filter, LocalDate dateFilter) {
        tableModel.setRowCount(0);

        boolean isAdmin = loggedInUser.role.equalsIgnoreCase("admin");

        for (Reservation r : reservations) {
            // User role filtering
            if (!isAdmin && !r.name.equalsIgnoreCase(loggedInUser.username)) {
                continue;
            }
            // Apply search filter if present
            if (filter != null && !filter.test(r)) {
                continue;
            }
            // Apply date filter if present
            if (dateFilter != null) {
                if (!r.dateTime.toLocalDate().equals(dateFilter)) {
                    continue;
                }
            }
            tableModel.addRow(r.toRow());
        }
        lblTotalCount.setText("Total Reservations: " + tableModel.getRowCount());
    }

    private static void cancelSelectedReservation() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(frame, "Please select a reservation to cancel.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Admin can cancel any, user can cancel own only
        String selectedName = (String) tableModel.getValueAt(selectedRow, 0);
        String selectedVehicle = (String) tableModel.getValueAt(selectedRow, 1);
        String selectedSpot = (String) tableModel.getValueAt(selectedRow, 2);
        String selectedDateTime = (String) tableModel.getValueAt(selectedRow, 3);

        if (!loggedInUser.role.equalsIgnoreCase("admin") && !selectedName.equalsIgnoreCase(loggedInUser.username)) {
            JOptionPane.showMessageDialog(frame, "You can only cancel your own reservations.", "Access Denied", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(frame,
                "Cancel reservation for:\nName: " + selectedName + "\nVehicle: " + selectedVehicle + "\nSpot: " + selectedSpot,
                "Confirm Cancel", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // Find and remove reservation
            reservations.removeIf(r ->
                r.name.equalsIgnoreCase(selectedName) &&
                r.vehicleNumber.equalsIgnoreCase(selectedVehicle) &&
                r.parkingSpot.equalsIgnoreCase(selectedSpot) &&
                r.dateTime.format(formatter).equals(selectedDateTime)
            );
            saveReservationsToFile();
            updateTable(null, null);
            JOptionPane.showMessageDialog(frame, "Reservation cancelled.");
        }
    }

    private static void logout() {
        int confirm = JOptionPane.showConfirmDialog(frame, "Logout?", "Confirm Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            frame.dispose();
            loggedInUser = null;
            reservations.clear();
            SwingUtilities.invokeLater(PRWithAuth::showLoginScreen);
        }
    }

    private static void loadReservationsFromFile() {
        reservations.clear();
        File file = new File(FILE_NAME);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length != 4) continue;
                String name = parts[0];
                String vehicle = parts[1];
                String spot = parts[2];
                LocalDateTime dt = LocalDateTime.parse(parts[3], formatter);
                reservations.add(new Reservation(name, vehicle, spot, dt));
            }
        } catch (IOException | DateTimeParseException e) {
            JOptionPane.showMessageDialog(frame, "Failed to load reservations from file.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void saveReservationsToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_NAME))) {
            for (Reservation r : reservations) {
                bw.write(r.name + "\t" + r.vehicleNumber + "\t" + r.parkingSpot + "\t" + r.dateTime.format(formatter));
                bw.newLine();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Failed to save reservations to file.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
