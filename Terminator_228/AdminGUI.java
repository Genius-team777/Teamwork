import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Окно администратора для ClubDatabase.
 * Запуск: java AdminGUI   (из той же папки, где запускается Main)
 * Работает с общим файлом club.db.txt и обновляется, когда консоль меняет базу.
 */
public class AdminGUI extends JFrame {

    private static final Color PRIMARY = new Color(79, 70, 229);
    private static final Color DANGER  = new Color(220, 38, 38);
    private static final Color SUCCESS = new Color(5, 150, 105);
    private static final Color NEUTRAL = new Color(75, 85, 99);
    private static final Color BG      = new Color(243, 244, 246);
    private static final Color BORDER  = new Color(209, 213, 219);
    private static final Color TEXT    = new Color(17, 24, 39);
    private static final Color ZEBRA   = new Color(249, 250, 251);
    private static final Color SELECT  = new Color(224, 231, 255);

    private final ClubDatabase db = ClubDatabase.getInstance();

    private final DefaultTableModel model =
        new DefaultTableModel(new String[]{"Имя", "Возраст", "Город", "Канал", "Контакт", "Жанры"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 1 ? Integer.class : String.class;
            }
        };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JLabel totalLabel = new JLabel();
    private final JLabel shownLabel = new JLabel();
    private JTextField searchField;
    private List<User> shown = new ArrayList<>();

    public AdminGUI() {
        super("Клуб книголюбов - база участников");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);
        setMinimumSize(new Dimension(860, 480));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        refresh();

        // Раз в секунду проверяем, не изменила ли базу другая программа (консоль)
        new javax.swing.Timer(1000, e -> {
            if (db.reloadIfChanged()) {
                refresh();
            }
        }).start();
    }

    // ======================= Интерфейс =======================

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PRIMARY);
        header.setBorder(new EmptyBorder(18, 24, 18, 24));

        JLabel title = new JLabel("Клуб знакомств молодых книголюбов");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Панель администратора - база участников (Singleton ClubDatabase, файл club.db.txt)");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(new Color(199, 210, 254));

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(sub);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(BG);
        center.setBorder(new EmptyBorder(16, 24, 8, 24));

        searchField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(156, 163, 175));
                    g2.setFont(getFont());
                    Insets in = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("Поиск по имени, городу, жанру, контакту...", in.left,
                                  (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            }
        };
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchField.setForeground(TEXT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER, 1, true), new EmptyBorder(9, 12, 9, 12)));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        center.add(searchField, BorderLayout.NORTH);

        styleTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(BORDER, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        center.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        buttons.setOpaque(false);

        RoundButton add    = new RoundButton("+ Добавить", SUCCESS);
        RoundButton edit   = new RoundButton("Изменить", PRIMARY);
        RoundButton delete = new RoundButton("Удалить", DANGER);
        RoundButton export = new RoundButton("Экспорт CSV", NEUTRAL);
        RoundButton imp    = new RoundButton("Импорт CSV", NEUTRAL);
        RoundButton check  = new RoundButton("Проверить Singleton", NEUTRAL);

        add.addActionListener(e -> addUser());
        edit.addActionListener(e -> editUser());
        delete.addActionListener(e -> deleteUser());
        export.addActionListener(e -> exportCsv());
        imp.addActionListener(e -> importCsv());
        check.addActionListener(e -> checkSingleton());

        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(export);
        buttons.add(imp);
        buttons.add(check);
        center.add(buttons, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(8, 24, 8, 24)));
        Font f = new Font("SansSerif", Font.PLAIN, 12);
        totalLabel.setFont(f);
        shownLabel.setFont(f);
        totalLabel.setForeground(NEUTRAL);
        shownLabel.setForeground(NEUTRAL);
        bar.add(totalLabel, BorderLayout.WEST);
        bar.add(shownLabel, BorderLayout.EAST);
        return bar;
    }

    private void styleTable() {
        table.setRowSorter(sorter);
        table.setRowHeight(36);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(229, 231, 235));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setIntercellSpacing(new Dimension(0, 1));

        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, false, row, col);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                setForeground(TEXT);
                setBackground(sel ? SELECT : (row % 2 == 0 ? Color.WHITE : ZEBRA));
                setHorizontalAlignment(col == 1 ? CENTER : LEFT);
                return this;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(cell);
        }
        table.getColumnModel().getColumn(1).setMaxWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(110);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 38));
        header.setDefaultRenderer((tbl, val, sel, foc, row, col) -> {
            JLabel l = new JLabel(String.valueOf(val));
            l.setOpaque(true);
            l.setBackground(new Color(238, 242, 255));
            l.setForeground(PRIMARY);
            l.setFont(new Font("SansSerif", Font.BOLD, 13));
            l.setBorder(new EmptyBorder(0, 12, 0, 12));
            l.setHorizontalAlignment(col == 1 ? SwingConstants.CENTER : SwingConstants.LEFT);
            return l;
        });

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    editUser();
                }
            }
        });
    }

    // ======================= Данные =======================

    private void refresh() {
        shown = new ArrayList<>(db.getUsers());
        model.setRowCount(0);
        for (User u : shown) {
            model.addRow(new Object[]{
                u.getName(), u.getAge(), u.getCity(), u.getChannel(),
                u.getContact(), String.join(", ", u.getGenres())
            });
        }
        updateStatus();
    }

    private void applyFilter() {
        String t = searchField.getText().trim();
        if (t.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(t)));
        }
        updateStatus();
    }

    private void updateStatus() {
        totalLabel.setText("Всего участников: " + db.count());
        shownLabel.setText("Показано: " + table.getRowCount());
    }

    private User selected() {
        int v = table.getSelectedRow();
        if (v < 0) {
            JOptionPane.showMessageDialog(this, "Сначала выберите участника в таблице.",
                "Ничего не выбрано", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return shown.get(table.convertRowIndexToModel(v));
    }

    // ======================= Действия =======================

    private void addUser() {
        User u = askUser("Новый участник", null);
        if (u == null) return;
        if (db.findByContact(u.getContact()) != null) {
            JOptionPane.showMessageDialog(this, "Этот контакт уже зарегистрирован.",
                "Дубликат", JOptionPane.WARNING_MESSAGE);
            return;
        }
        db.register(u);
        refresh();
    }

    private void editUser() {
        User old = selected();
        if (old == null) return;
        User updated = askUser("Изменить участника", old);
        if (updated == null) return;
        if (!db.replaceUser(old, updated)) {
            JOptionPane.showMessageDialog(this, "Не удалось сохранить: такой контакт уже занят.",
                "Ошибка", JOptionPane.WARNING_MESSAGE);
        }
        refresh();
    }

    private void deleteUser() {
        User u = selected();
        if (u == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
            "Удалить участника «" + u.getName() + "»?",
            "Подтверждение", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.YES_OPTION) {
            db.remove(u.getContact());
            refresh();
        }
    }

    private void checkSingleton() {
        boolean same = ClubDatabase.getInstance() == db;
        JOptionPane.showMessageDialog(this,
            "Тот же объект базы внутри этой программы: " + same
                + "\nХеш объекта: " + System.identityHashCode(db)
                + "\n\nФайл данных, общий с консольной программой:\n"
                + new File("club.db.txt").getAbsolutePath(),
            "Проверка Singleton", JOptionPane.INFORMATION_MESSAGE);
    }

    // Диалог ввода участника; null - если нажата отмена
    private User askUser(String title, User src) {
        JTextField nameF = new JTextField(src == null ? "" : src.getName(), 22);
        JSpinner ageS = new JSpinner(new SpinnerNumberModel(src == null ? 18 : src.getAge(), 1, 120, 1));
        JTextField cityF = new JTextField(src == null ? "Алматы" : src.getCity(), 22);
        JComboBox<String> chanC = new JComboBox<>(ClubDatabase.getChannels().toArray(new String[0]));
        if (src != null) chanC.setSelectedItem(src.getChannel());
        JTextField contactF = new JTextField(src == null ? "" : src.getContact(), 22);
        JTextField genresF = new JTextField(src == null ? "" : String.join(", ", src.getGenres()), 22);

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(p, c, 0, "Имя:", nameF);
        addRow(p, c, 1, "Возраст:", ageS);
        addRow(p, c, 2, "Город:", cityF);
        addRow(p, c, 3, "Канал связи:", chanC);
        addRow(p, c, 4, "Контакт:", contactF);
        addRow(p, c, 5, "Жанры (через запятую):", genresF);

        while (true) {
            int res = JOptionPane.showConfirmDialog(this, p, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return null;

            User u = new User(nameF.getText().trim(), (Integer) ageS.getValue(),
                cityF.getText().trim(), (String) chanC.getSelectedItem(),
                contactF.getText().trim());
            for (String g : genresF.getText().split(",")) {
                if (!g.trim().isEmpty()) u.addGenre(g.trim());
            }
            String err = ClubDatabase.validationError(u);
            if (err != null) {
                JOptionPane.showMessageDialog(this, "Нельзя сохранить: " + err,
                    "Проверка данных", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return u;
        }
    }

    private static void addRow(JPanel p, GridBagConstraints c, int row,
                               String label, JComponent field) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        p.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        p.add(field, c);
    }

    // ======================= CSV =======================

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("club_members.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter out = new PrintWriter(fc.getSelectedFile(), "UTF-8")) {
            out.println("name,age,city,channel,contact,genres");
            for (User u : db.getUsers()) {
                out.println(csv(u.getName()) + "," + u.getAge() + "," + csv(u.getCity()) + ","
                          + csv(u.getChannel()) + "," + csv(u.getContact()) + ","
                          + csv(String.join(";", u.getGenres())));
            }
            JOptionPane.showMessageDialog(this, "Экспортировано участников: " + db.count(),
                "Экспорт", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка экспорта: " + ex.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importCsv() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int ok = JOptionPane.showConfirmDialog(this,
            "Импорт ЗАМЕНИТ всех текущих участников. Продолжить?",
            "Подтверждение импорта", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        List<User> loaded = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty() || line.toLowerCase().startsWith("name,")) continue;
                List<String> f = parseCsv(line);
                if (f.size() < 6) continue;
                User u = new User(f.get(0), Integer.parseInt(f.get(1).trim()),
                    f.get(2), f.get(3), f.get(4));
                for (String g : f.get(5).split(";")) {
                    if (!g.trim().isEmpty()) u.addGenre(g.trim());
                }
                if (ClubDatabase.validationError(u) == null) loaded.add(u);
            }
        } catch (IOException | NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка импорта: " + ex.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        db.clearUsers();
        for (User u : loaded) {
            db.register(u);
        }
        refresh();
    }

    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static List<String> parseCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        out.add(sb.toString());
        return out;
    }

    // ======================= Кнопка со скруглением =======================

    private static class RoundButton extends JButton {
        private final Color base;
        private boolean hover;

        RoundButton(String text, Color base) {
            super(text);
            this.base = base;
            setForeground(Color.WHITE);
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(new EmptyBorder(10, 18, 10, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? base.darker() : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminGUI().setVisible(true));
    }
}