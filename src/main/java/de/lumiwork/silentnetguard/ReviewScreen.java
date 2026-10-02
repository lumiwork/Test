package de.lumiwork.silentnetguard;

import de.lumiwork.silentnetguard.scan.Category;
import de.lumiwork.silentnetguard.scan.Finding;
import de.lumiwork.silentnetguard.scan.Origin;
import de.lumiwork.silentnetguard.scan.ScanResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Prüf-Fenster "InjectedMod Assist". Laeuft in einem eigenen Java-Prozess (kein AWT im Minecraft-Prozess).
 * Gibt die Entscheidung als Zeile "DECISION:proceed|abort|quarantine" auf stdout aus.
 * Bei Quarantäne wartet der Prozess, bis Minecraft beendet ist, und verschiebt dann die Dateien.
 *
 * Argumente: reportFile parentPid quarantineDir allowlistFile
 */
public final class ReviewScreen {

    private static final Color RED = new Color(0xC6, 0x28, 0x28);
    private static final Color ORANGE = new Color(0xE0, 0x7B, 0x00);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color GREY = new Color(0x75, 0x75, 0x75);

    private final Report report;
    private final long parentPid;
    private final Path quarantineDir;
    private final Path allowlistFile;
    private final List<Row> allRows = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final RowModel model = new RowModel();
    private JFrame frame;
    private JTable table;
    private JEditorPane details;
    private boolean decided;

    /** Eine Tabellenzeile: Mod (Top-Level) oder eingebettete Library. */
    private static final class Row {
        final ScanResult result;
        final ScanResult topLevel;
        final int depth;
        boolean quarantine;

        Row(ScanResult result, ScanResult topLevel, int depth) {
            this.result = result;
            this.topLevel = topLevel;
            this.depth = depth;
            this.quarantine = depth == 0 && result.worstLevel() == ScanResult.Level.MALICIOUS;
        }
    }

    private ReviewScreen(Report report, long parentPid, Path quarantineDir, Path allowlistFile) {
        this.report = report;
        this.parentPid = parentPid;
        this.quarantineDir = quarantineDir;
        this.allowlistFile = allowlistFile;
        List<ScanResult> sorted = new ArrayList<>(report.mods);
        sorted.sort(Comparator.<ScanResult>comparingInt(m -> m.worstLevel().ordinal())
                .thenComparing(m -> m.origin.status() != Origin.Status.MODIFIED)
                .thenComparing(m -> -totalScore(m))
                .thenComparing(ScanResult::title, String.CASE_INSENSITIVE_ORDER));
        for (ScanResult m : sorted) {
            addRows(m, m, 0);
        }
    }

    public static void main(String[] args) throws Exception {
        Report report = Report.read(Path.of(args[0]));
        ReviewScreen screen = new ReviewScreen(report, Long.parseLong(args[1]), Path.of(args[2]), Path.of(args[3]));
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Standard-Look
        }
        SwingUtilities.invokeAndWait(screen::show);
    }

    private void addRows(ScanResult r, ScanResult top, int depth) {
        allRows.add(new Row(r, top, depth));
        for (ScanResult c : r.children) {
            addRows(c, top, depth + 1);
        }
    }

    private static int totalScore(ScanResult r) {
        int s = r.score();
        for (ScanResult c : r.children) {
            s = Math.max(s, totalScore(c));
        }
        return s;
    }

    // ------------------------------------------------------------------ Aufbau

    private void show() {
        frame = new JFrame("SilentNet Guard - InjectedMod Assist");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                decide("abort");
            }
        });

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(header(), BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setMaxWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(230);
        table.getColumnModel().getColumn(4).setPreferredWidth(170);
        table.getColumnModel().getColumn(5).setMaxWidth(60);
        StatusRenderer renderer = new StatusRenderer();
        for (int c = 1; c < 6; c++) {
            table.getColumnModel().getColumn(c).setCellRenderer(renderer);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails();
            }
        });

        details = new JEditorPane("text/html", "");
        details.setEditable(false);
        details.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(details));
        split.setResizeWeight(0.55);
        root.add(split, BorderLayout.CENTER);
        root.add(buttons(), BorderLayout.SOUTH);

        refreshRows(false);
        frame.setContentPane(root);
        frame.setSize(new Dimension(1100, 720));
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        frame.toFront();
        frame.setAlwaysOnTop(false);
        if (!rows.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private JPanel header() {
        long malicious = report.mods.stream().filter(m -> m.worstLevel() == ScanResult.Level.MALICIOUS).count();
        long suspicious = report.mods.stream().filter(m -> m.worstLevel() == ScanResult.Level.SUSPICIOUS).count();
        long modified = report.mods.stream().filter(m -> m.origin.status() == Origin.Status.MODIFIED).count();
        long original = report.mods.stream().filter(m -> m.origin.status() == Origin.Status.ORIGINAL).count();
        boolean critical = report.system.stream().anyMatch(SystemCheck.Issue::critical);

        Color color;
        String title;
        if (malicious > 0 || critical) {
            color = RED;
            title = malicious > 0 ? malicious + (malicious == 1 ? " schädliche Mod gefunden!" : " schädliche Mods gefunden!")
                    : "Dein System ist wahrscheinlich bereits infiziert!";
        } else if (suspicious > 0 || modified > 0 || !report.system.isEmpty()) {
            color = ORANGE;
            title = "Auffälligkeiten gefunden - bitte prüfen";
        } else {
            color = GREEN;
            title = "Keine Bedrohungen gefunden";
        }
        String online = !report.onlineChecked ? "Herkunftsprüfung deaktiviert"
                : report.online ? original + " Original-Dateien bestätigt (Modrinth/Maven Central)"
                : "Herkunft nicht prüfbar (offline)";
        String sub = report.mods.size() + " Mods und " + report.libraryCount() + " eingebettete Libraries geprüft  |  "
                + suspicious + " verdächtig  |  " + modified + " nicht original  |  " + online;

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(color);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel t = new JLabel(title);
        t.setForeground(Color.WHITE);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 20f));
        JLabel s = new JLabel(sub);
        s.setForeground(Color.WHITE);
        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(s);
        for (SystemCheck.Issue issue : report.system) {
            JLabel l = new JLabel((issue.critical() ? "⚠ " : "? ") + issue.text());
            l.setForeground(Color.WHITE);
            l.setFont(l.getFont().deriveFont(issue.critical() ? Font.BOLD : Font.PLAIN));
            p.add(Box.createVerticalStrut(3));
            p.add(l);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buttons() {
        JCheckBox showLibs = new JCheckBox("Alle eingebetteten Libraries anzeigen");
        showLibs.addActionListener(e -> refreshRows(showLibs.isSelected()));

        JButton trust = new JButton("Als vertrauenswürdig markieren");
        trust.setToolTipText("Fuegt den SHA-256 der ausgewählten Mod zur Allowlist hinzu - sie wird künftig nicht mehr gemeldet");
        trust.addActionListener(e -> trustSelected());

        JButton quarantine = new JButton("Quarantäne & beenden");
        quarantine.addActionListener(e -> {
            List<String> files = quarantineFiles();
            if (files.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Keine Mod für die Quarantäne markiert (Spalte \"Quarantäne\").");
                return;
            }
            decide("quarantine");
        });
        JButton abort = new JButton("Start abbrechen");
        abort.addActionListener(e -> decide("abort"));
        JButton proceed = new JButton("Trotzdem starten");
        proceed.addActionListener(e -> {
            if (report.anyMalicious()) {
                int ok = JOptionPane.showConfirmDialog(frame,
                        "Es wurde Malware gefunden. Wenn du startest, kann dein Minecraft-Account gestohlen werden.\n"
                                + "Wirklich starten?", "Wirklich starten?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ok != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            decide("proceed");
        });
        if (report.anyMalicious()) {
            quarantine.setForeground(RED);
            frame.getRootPane().setDefaultButton(abort);
        } else {
            frame.getRootPane().setDefaultButton(proceed);
        }

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(showLibs);
        left.add(trust);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(quarantine);
        right.add(abort);
        right.add(proceed);
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.add(left, BorderLayout.NORTH);
        p.add(right, BorderLayout.SOUTH);
        return p;
    }

    private void refreshRows(boolean showAllLibraries) {
        rows.clear();
        for (Row r : allRows) {
            boolean interesting = r.result.worstLevel() != ScanResult.Level.CLEAN
                    || r.result.origin.status() == Origin.Status.MODIFIED;
            if (r.depth == 0 || showAllLibraries || interesting) {
                rows.add(r);
            }
        }
        model.fireTableDataChanged();
    }

    // ------------------------------------------------------------------ Aktionen

    private void showDetails() {
        int i = table.getSelectedRow();
        if (i < 0 || i >= rows.size()) {
            return;
        }
        ScanResult r = rows.get(i).result;
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'>");
        h.append("<h2 style='margin:0'>").append(esc(r.title())).append("</h2>");
        ScanResult.Level shown = rows.get(i).depth == 0 ? r.worstLevel() : r.level();
        h.append("<p style='color:").append(hex(color(shown))).append("'><b>").append(shown.label).append("</b>");
        if (shown != r.level()) {
            h.append(" &nbsp; (wegen einer eingebetteten Library, siehe unten)");
        }
        h.append(" &nbsp; Punkte dieser Datei: ").append(r.score()).append(" (ab ").append(ScanResult.SUSPICIOUS)
                .append(" verdächtig, ab ").append(ScanResult.MALICIOUS).append(" schädlich)</p>");
        h.append("<table cellpadding='1'>");
        row(h, "Datei", r.file != null ? r.file : r.display);
        row(h, "Mod-ID", r.modId == null ? "-" : r.modId);
        row(h, "Herkunft", r.origin.status().label + (r.origin.detail().isEmpty() ? "" : " - " + r.origin.detail()));
        row(h, "SHA-256", r.sha256);
        h.append("</table>");
        if (r.findings.isEmpty()) {
            h.append("<p>Keine Auffälligkeiten in dieser Datei.</p>");
        } else {
            Map<Category, List<Finding>> byCat = new LinkedHashMap<>();
            for (Category c : Category.values()) {
                for (Finding f : r.findings) {
                    if (f.category() == c) {
                        byCat.computeIfAbsent(c, k -> new ArrayList<>()).add(f);
                    }
                }
            }
            for (Map.Entry<Category, List<Finding>> e : byCat.entrySet()) {
                h.append("<h3 style='margin-bottom:2px'>").append(esc(e.getKey().label)).append("</h3><ul style='margin-top:2px'>");
                for (Finding f : e.getValue()) {
                    h.append("<li><b>+").append(f.points()).append("</b> ").append(esc(f.text())).append("</li>");
                }
                h.append("</ul>");
            }
        }
        List<ScanResult> badChildren = new ArrayList<>();
        collectBad(r, badChildren);
        if (!badChildren.isEmpty()) {
            h.append("<h3>Auffällige eingebettete Libraries</h3><ul>");
            for (ScanResult c : badChildren) {
                h.append("<li>").append(esc(c.display)).append(" - <b>").append(c.level().label).append("</b> (")
                        .append(c.score()).append(" Punkte)</li>");
            }
            h.append("</ul>");
        }
        details.setText(h.append("</body></html>").toString());
        details.setCaretPosition(0);
    }

    private static void collectBad(ScanResult r, List<ScanResult> out) {
        for (ScanResult c : r.children) {
            if (c.level() != ScanResult.Level.CLEAN) {
                out.add(c);
            }
            collectBad(c, out);
        }
    }

    private void trustSelected() {
        int i = table.getSelectedRow();
        if (i < 0) {
            return;
        }
        Row row = rows.get(i);
        if (row.depth != 0) {
            JOptionPane.showMessageDialog(frame, "Bitte die Mod selbst auswählen, nicht eine eingebettete Library.");
            return;
        }
        ScanResult r = row.result;
        int ok = JOptionPane.showConfirmDialog(frame, "\"" + r.title() + "\" künftig nicht mehr melden?\n"
                + "Nur machen, wenn du sicher bist, dass die Datei aus einer vertrauenswürdigen Quelle stammt.",
                "Als vertrauenswürdig markieren", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.createDirectories(allowlistFile.getParent());
            Files.writeString(allowlistFile, r.sha256 + "  # " + r.title() + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            markTrusted(r);
            row.quarantine = false;
            model.fireTableDataChanged();
            table.setRowSelectionInterval(i, i);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Konnte Allowlist nicht schreiben: " + ex.getMessage());
        }
    }

    private static void markTrusted(ScanResult r) {
        r.allowlisted = true;
        r.origin = new Origin(Origin.Status.TRUSTED, "Von dir als vertrauenswürdig markiert");
        r.children.forEach(ReviewScreen::markTrusted);
    }

    private List<String> quarantineFiles() {
        List<String> files = new ArrayList<>();
        for (Row r : allRows) {
            if (r.depth == 0 && r.quarantine && r.result.file != null) {
                files.add(r.result.file);
            }
        }
        return files;
    }

    private void decide(String decision) {
        if (decided) {
            return;
        }
        decided = true;
        List<String> files = decision.equals("quarantine") ? quarantineFiles() : List.of();
        System.out.println("DECISION:" + decision);
        System.out.flush();
        frame.dispose();
        if (files.isEmpty()) {
            System.exit(0);
        }
        // Minecraft beendet sich jetzt; erst danach sind die Dateien nicht mehr gesperrt.
        new Thread(() -> quarantine(files), "quarantine").start();
    }

    private void quarantine(List<String> files) {
        ProcessHandle.of(parentPid).ifPresent(p -> {
            try {
                p.onExit().get(120, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // weiter versuchen
            }
        });
        List<String> moved = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String f : files) {
            Path src = Path.of(f);
            Path dst = quarantineDir.resolve(src.getFileName() + ".disabled");
            boolean ok = false;
            for (int attempt = 0; attempt < 20 && !ok; attempt++) {
                try {
                    Files.createDirectories(quarantineDir);
                    Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    ok = true;
                } catch (IOException e) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            (ok ? moved : failed).add(f);
        }
        StringBuilder msg = new StringBuilder();
        if (!moved.isEmpty()) {
            msg.append("In Quarantäne verschoben nach\n").append(quarantineDir).append(":\n");
            moved.forEach(m -> msg.append("  - ").append(Path.of(m).getFileName()).append('\n'));
        }
        if (!failed.isEmpty()) {
            msg.append("\nKonnte nicht verschoben werden - bitte von Hand löschen:\n");
            failed.forEach(m -> msg.append("  - ").append(m).append('\n'));
        }
        msg.append("\nAendere jetzt dein Microsoft-Passwort und melde dich unter account.microsoft.com überall ab.");
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null, msg.toString(), "SilentNet Guard",
                    failed.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE));
        } catch (Exception ignored) {
            System.out.println(msg);
        }
        System.exit(0);
    }

    // ------------------------------------------------------------------ Tabelle

    private final class RowModel extends AbstractTableModel {
        private final String[] columns = {"Quarantäne", "Status", "Mod / Library", "Datei", "Herkunft", "Punkte"};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int c) {
            return columns[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : c == 5 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0 && rows.get(r).depth == 0 && rows.get(r).result.file != null;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            ScanResult s = row.result;
            return switch (c) {
                case 0 -> row.depth == 0 && row.quarantine;
                case 1 -> row.depth == 0 ? s.worstLevel().label : s.level().label;
                case 2 -> "    ".repeat(row.depth) + (row.depth > 0 ? "↳ " : "") + s.title();
                case 3 -> s.display.substring(s.display.lastIndexOf('/') + 1);
                case 4 -> s.origin.status().label;
                default -> s.score();
            };
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) {
                rows.get(r).quarantine = Boolean.TRUE.equals(v);
            }
        }
    }

    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            Row row = rows.get(r);
            ScanResult s = row.result;
            setFont(getFont().deriveFont(row.depth == 0 ? Font.PLAIN : Font.ITALIC));
            if (!sel) {
                setForeground(Color.BLACK);
                if (c == 1) {
                    ScanResult.Level l = row.depth == 0 ? s.worstLevel() : s.level();
                    setForeground(l == ScanResult.Level.MALICIOUS ? RED : l == ScanResult.Level.SUSPICIOUS ? ORANGE : GREEN);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (c == 4) {
                    Origin.Status o = s.origin.status();
                    setForeground(o == Origin.Status.MODIFIED ? RED : o == Origin.Status.ORIGINAL
                            || o == Origin.Status.KNOWN_LIBRARY || o == Origin.Status.TRUSTED ? GREEN : GREY);
                    if (o == Origin.Status.MODIFIED) {
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                }
            }
            setToolTipText(c == 4 ? s.origin.detail() : c == 3 ? s.display : null);
            return this;
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static Color color(ScanResult.Level level) {
        return switch (level) {
            case MALICIOUS -> RED;
            case SUSPICIOUS -> ORANGE;
            default -> GREEN;
        };
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static void row(StringBuilder h, String k, String v) {
        h.append("<tr><td valign='top'><b>").append(k).append(":</b></td><td>").append(esc(v)).append("</td></tr>");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
