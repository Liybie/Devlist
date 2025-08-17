import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class DevList extends JFrame {

    // ==== UI Components ====
    private DefaultTableModel model;          // Table model for active tasks
    private JTable table;                     // Main task table
    private JLabel statsLabel;                // Shows task statistics

    private DefaultTableModel finishedModel;  // Table model for finished tasks
    private JFrame finishedFrame;             // Separate window for finished tasks

    // ==== Task Counters ====
    private int completed = 0, inProgress = 0;

    // ==== Preloaded Task Categories ====
    private Map<String, List<String[]>> preloadedCategories = new LinkedHashMap<>();

    // ==== Undo Stack (LIFO) ====
    private Deque<Object[]> undoStack = new ArrayDeque<>(); // LIFO for undo functionality

    // ==== Task Queue (Priority) ====
    private Queue<Object[]> taskQueue = new PriorityQueue<>(
        (a, b) -> {
            List<String> order = Arrays.asList("High", "Medium", "Low");
            // FIX: queue stores {id, priority}; compare index 1 not 2
            return Integer.compare(order.indexOf(a[1].toString()), order.indexOf(b[1].toString()));
        });

    // ==== Task ID Support ====
    private int taskCounter = 1; // unique ID generator

    /** Constructor: Initialize App */
    public DevList() {
        super("DevList");

        setupPreloadedTasks();  // Load initial tasks
        setupFrame();           // Setup JFrame properties
        setupTopPanel();        // Setup the top UI panel (logo, stats, buttons)
        setupTable();           // Setup task table panel
        setupFinishedFrame();   // Prepare separate finished-task frame
        updateStats();          // Initialize stats display
    }

    // ==== Preloaded Tasks Setup ====
    private void setupPreloadedTasks() {
        preloadedCategories.put("Designing", Arrays.asList(
                new String[]{"Create Wireframes", "Design page wireframes", "High"},
                new String[]{"Choose Color Scheme", "Pick colors for UI/UX", "Medium"},
                new String[]{"Design Mockups", "Create high-fidelity mockups", "High"}));

        preloadedCategories.put("Frontend", Arrays.asList(
                new String[]{"Build Homepage", "Create responsive homepage", "High"},
                new String[]{"Setup CSS Grid", "Use CSS Grid for layout design", "Medium"},
                new String[]{"Add JS Slider", "Implement image slider", "High"},
                new String[]{"Responsive Navbar", "Navbar collapses on mobile", "Low"},
                new String[]{"SEO Optimization", "Optimize meta tags", "Medium"}));

        preloadedCategories.put("Backend", Arrays.asList(
                new String[]{"Setup Database", "Create MySQL/PostgreSQL DB", "High"},
                new String[]{"API Endpoints", "Create REST API endpoints", "High"},
                new String[]{"User Authentication", "Login/register system", "Medium"}));
    }

    // ==== Frame Setup ====
    private void setupFrame() {
        setSize(950, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
    }

    // ==== Top Panel Setup ====
    private void setupTopPanel() {
        JPanel top = new JPanel(new BorderLayout(10, 10));

        // --- Left Section Logo + Stats ---
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        // Logo
        JLabel logo = new JLabel("DevList");
        leftPanel.add(logo);

        // Stats
        statsLabel = new JLabel();
        leftPanel.add(statsLabel);

        top.add(leftPanel, BorderLayout.WEST);

        // --- Right Panel (Buttons & Sorting Options) ---
        top.add(createRightPanel(), BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
    }

    /** Creates right-side panel for buttons and sorting */
    private JPanel createRightPanel() {
        JPanel right = new JPanel(new GridLayout(3, 1, 5, 5));

        // --- Buttons Panel ---
        JPanel btnPanel = new JPanel();
        JButton newBtn = new JButton("+ NEW");
        newBtn.addActionListener(e -> openAddOptionDialog());

        JButton delBtn = new JButton("DELETE");
        delBtn.addActionListener(e -> deleteSelected());

        JButton undoBtn = new JButton("UNDO");
        undoBtn.addActionListener(e -> undoLastAction());

        JButton finishedBtn = new JButton("COMPLETED");
        finishedBtn.addActionListener(e -> finishedFrame.setVisible(true));

        // ==== QUEUE CODE (Priority) ==== add Process Next button
        JButton nextBtn = new JButton("PROCESS NEXT");
        nextBtn.addActionListener(e -> processNextTask());

        btnPanel.add(newBtn);
        btnPanel.add(delBtn);
        btnPanel.add(undoBtn);
        btnPanel.add(finishedBtn);
        btnPanel.add(nextBtn);

        // --- Sorting Panel ---
        JPanel sortPanel = new JPanel();
        JButton sortDate = new JButton("Sort by Date Added");
        sortDate.addActionListener(e -> sortByDateAdded());
        JButton sortPri = new JButton("Sort by Priority");
        sortPri.addActionListener(e -> sortByPriority());

        sortPanel.add(new JLabel("Sort By:"));
        sortPanel.add(sortDate);
        sortPanel.add(sortPri);

        right.add(btnPanel);
        right.add(sortPanel);

        return right;
    }

    // ==== Task Table Setup ====
    private void setupTable() {
        // Note: Hidden ID column at the end
        String[] cols = {"Status", "Task Name", "Description", "Date Added", "Priority", "ID"};
        model = new DefaultTableModel(cols, 0) {
            public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            public boolean isCellEditable(int r, int c) { return c != 5; } // prevent editing ID
        };

        table = new JTable(model);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);

        // Hide ID column from user
        TableColumn idCol = table.getColumnModel().getColumn(5);
        table.removeColumn(idCol);

        // Checkbox listener for completion
        model.addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                boolean checked = (Boolean) model.getValueAt(e.getFirstRow(), 0);
                if (checked) {
                    moveToFinished(e.getFirstRow());
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    // ==== Finished Task Frame ====
    private void setupFinishedFrame() {
        finishedModel = new DefaultTableModel(new String[]{"Task Name", "Description", "Date Added", "Priority", "ID"}, 0);
        JTable finishedTable = new JTable(finishedModel);
        finishedTable.setRowHeight(25);

        // Hide ID column
        TableColumn idCol = finishedTable.getColumnModel().getColumn(4);
        finishedTable.removeColumn(idCol);

        finishedFrame = new JFrame("Completed Tasks");
        finishedFrame.setSize(600, 400);
        finishedFrame.add(new JScrollPane(finishedTable));
        finishedFrame.setLocationRelativeTo(this);
    }

    // ==== Move to Finished ====
    private void moveToFinished(int row) {
        Object[] data = new Object[5];
        data[0] = model.getValueAt(row, 1);
        data[1] = model.getValueAt(row, 2);
        data[2] = model.getValueAt(row, 3);
        data[3] = model.getValueAt(row, 4);
        data[4] = model.getValueAt(row, 5); // ID
        finishedModel.addRow(data);

        undoStack.push(new Object[]{"finish", data}); // store for undo (LIFO)

        model.removeRow(row);
        removeFromQueueById((Integer) data[4]);

        completed++;
        inProgress--;
        updateStats();
    }

    // ==== Undo Last Action (LIFO) ====
    private void undoLastAction() {
        if (!undoStack.isEmpty()) {
            Object[] last = undoStack.pop();
            String action = (String) last[0];
            Object[] data = (Object[]) last[1];

            switch (action) {
                case "delete": {
                    model.addRow(data);
                    //Queue stores {id, priority} - indices 5 (ID), 4 (Priority)
                    taskQueue.offer(new Object[]{data[5], data[4]});
                    inProgress++;
                    break;
                }
                case "finish":
                case "process": {
                    model.addRow(new Object[]{false, data[0], data[1], data[2], data[3], data[4]});
                    removeFromFinishedById((Integer) data[4]);
                    taskQueue.offer(new Object[]{data[4], data[3]});
                    completed--;
                    inProgress++;
                    break;
                }
                default:
                    break;
            }
            updateStats();
        }
    }

    // ==== Add Task Dialog ====
    private void openAddOptionDialog() {
        String[] options = {"Add Preloaded Category Tasks", "Add Custom Task"};
        int choice = JOptionPane.showOptionDialog(this,
                "Select how you want to add tasks:",
                "Add Task",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) openCategorySelectionDialog();
        else if (choice == 1) openCustomTaskDialog();
    }

    private void openCategorySelectionDialog() {
        String[] categories = preloadedCategories.keySet().toArray(new String[0]);
        String cat = (String) JOptionPane.showInputDialog(this, "Select a category to auto-add tasks:",
                "Preloaded Categories", JOptionPane.PLAIN_MESSAGE, null, categories, categories[0]);
        if (cat != null) preloadedCategories.get(cat).forEach(t -> addTaskToTable(t[0], t[1], t[2]));
    }

    private void openCustomTaskDialog() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        JTextField nameF = new JTextField(), descF = new JTextField();
        JComboBox<String> prioBox = new JComboBox<>(new String[]{"Low","Medium","High"});

        panel.add(new JLabel("Task Name:")); panel.add(nameF);
        panel.add(new JLabel("Description:")); panel.add(descF);
        panel.add(new JLabel("Priority:")); panel.add(prioBox);

        if (JOptionPane.showConfirmDialog(this, panel, "Add Custom Task", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION
                && !nameF.getText().trim().isEmpty()) {
            addTaskToTable(nameF.getText(), descF.getText(), prioBox.getSelectedItem().toString());
        }
    }

    // ==== Add Task Helper ====
    private void addTaskToTable(String name, String desc, String priority) {
        String added = LocalDate.now().toString();
        int id = taskCounter++;
        model.addRow(new Object[]{false, name, desc, added, priority, id});
        inProgress++;
        updateStats();
        sortByPriority();

        taskQueue.offer(new Object[]{id, priority});
    }

    // ==== Delete Selected Task ====
    private void deleteSelected() {
        Arrays.stream(table.getSelectedRows())
                .boxed()
                .sorted(Collections.reverseOrder())
                .forEach(row -> {
                    Object[] data = new Object[6];
                    for (int i = 0; i < 6; i++) data[i] = model.getValueAt(row, i);
                    undoStack.push(new Object[]{"delete", data});

                    removeFromQueueById((Integer) data[5]);

                    boolean done = (Boolean) model.getValueAt(row, 0);
                    if (done) completed--; else inProgress--;
                    model.removeRow(row);
                });
        updateStats();
    }

    // ==== QUEUE CODE (Priority): Process Next Task ====
    private void processNextTask() {
        if (!taskQueue.isEmpty()) {
            Object[] nextTask = taskQueue.poll();
            int taskId = (Integer) nextTask[0];

            Object[] rowData = null;
            for (int i = 0; i < model.getRowCount(); i++) {
                if (((Integer) model.getValueAt(i, 5)) == taskId) {
                    rowData = new Object[]{model.getValueAt(i, 1), model.getValueAt(i, 2), model.getValueAt(i, 3), model.getValueAt(i, 4), taskId};
                    model.removeRow(i);
                    break;
                }
            }

            if (rowData != null) {
                finishedModel.addRow(rowData);
                undoStack.push(new Object[]{"process", rowData});
                completed++;
                inProgress--;
                updateStats();
                JOptionPane.showMessageDialog(this, "Processed (Priority): " + rowData[0]);
            }
        } else {
            JOptionPane.showMessageDialog(this, "No tasks in queue!");
        }
    }

    // ==== Sorting Functions ====
    private void sortByDateAdded() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<Vector> rows = new ArrayList<>(model.getDataVector());
        rows.sort((a,b) -> {
            try {
                return LocalDate.parse(a.get(3).toString(), f).compareTo(LocalDate.parse(b.get(3).toString(), f));
            } catch(Exception ex) { return 0; }
        });
        model.setRowCount(0); rows.forEach(model::addRow);
    }

    private void sortByPriority() {
        List<String> order = Arrays.asList("High","Medium","Low");
        List<Vector> rows = new ArrayList<>(model.getDataVector());
        rows.sort((a,b)-> Integer.compare(order.indexOf(a.get(4)), order.indexOf(b.get(4))));
        model.setRowCount(0); rows.forEach(model::addRow);
    }

    // ==== Update Stats ====
    private void updateStats() {
        statsLabel.setText("Tasks: "+model.getRowCount()+" | Completed: "+completed+" | In Progress: "+inProgress);
    }

    // ==== Helpers ====
    private void removeFromQueueById(int id) {
        for (Iterator<Object[]> it = taskQueue.iterator(); it.hasNext(); ) {
            Object[] t = it.next();
            if ((Integer) t[0] == id) {
                it.remove();
                break;
            }
        }
    }

    private void removeFromFinishedById(int id) {
        for (int i = 0; i < finishedModel.getRowCount(); i++) {
            if ((Integer) finishedModel.getValueAt(i, 4) == id) {
                finishedModel.removeRow(i);
                break;
            }
        }
    }

    // ==== Main Method ====
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DevList().setVisible(true));
    }
}
