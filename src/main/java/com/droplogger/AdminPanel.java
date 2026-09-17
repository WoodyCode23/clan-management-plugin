package com.droplogger;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AdminPanel extends JPanel implements Scrollable
{
    // The admin tab's scroll pane forbids horizontal scrolling; without tracking the
    // viewport width, any component wider than the sidebar is silently CLIPPED.
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 14; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font SMALL_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font SMALL_ITALIC = new Font("Segoe UI", Font.ITALIC, 11);

    private final JLabel statusLabel = new JLabel("Admin ready");

    // ── Shared Settings (managed via web dashboard) ──

    // ── Weekly Events ──
    private final JComboBox<String> eventTypeBox = new JComboBox<>(new String[]{"Boss of the Week", "Skill of the Week", "Gamer of the Week", "Clue Hunter of the Week"});
    private final JComboBox<String> eventMetricBox = new JComboBox<>();
    private final JTextField eventNameField = new JTextField();
    private final JTextField eventStartField = new JTextField();
    private final JTextField eventEndField = new JTextField();
    private final JPanel eventsListPanel = new JPanel();
    private Runnable onLoadEvents;
    private Consumer<String> onCancelEvent;

    // ── Rotate API Key ──
    private final JTextField newApiKeyField = new JTextField();
    private final JLabel newBoardCodeLabel = new JLabel(" ");

    // ── Announcements ──
    private final JPanel announcementsListPanel = new JPanel();
    private final JTextArea announcementInput = new JTextArea(2, 20);
    private final JCheckBox announcementPinned = new JCheckBox("Pin");
    private Consumer<Object[]> onCreateAnnouncement;     // {String message, Boolean pinned}
    private Consumer<String[]> onEditAnnouncement;       // {id, message}
    private Consumer<Object[]> onTogglePinAnnouncement;  // {String id, Boolean pinned}
    private Consumer<String> onDeleteAnnouncement;       // id

    // Callbacks
    private Consumer<String[]> onSaveSettings;
    private Runnable onLoadSettings;
    private Consumer<String[]> onRemoveHiscore;
    private Consumer<String> onRotateApiKey;
    private Consumer<String[]> onStartEvent; // {type, metric, name, days, startsInDays}
    private Runnable onSyncRoster;

    public AdminPanel()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(6, 4, 6, 4));

        // Header
        JLabel header = new JLabel("Admin Tools");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setForeground(new Color(255, 100, 100));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(8));

        // Shared settings are managed via the web dashboard

        // ══════════════════════════════════
        // Announcements
        // ══════════════════════════════════
        add(createSectionTitle("Announcements"));
        add(Box.createVerticalStrut(4));

        announcementsListPanel.setLayout(new BoxLayout(announcementsListPanel, BoxLayout.Y_AXIS));
        announcementsListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        announcementsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(announcementsListPanel);
        add(Box.createVerticalStrut(4));

        announcementInput.setLineWrap(true);
        announcementInput.setWrapStyleWord(true);
        announcementInput.setFont(SMALL_FONT);
        JScrollPane inputScroll = new JScrollPane(announcementInput);
        inputScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        add(inputScroll);
        add(Box.createVerticalStrut(2));

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        announcementPinned.setBackground(ColorScheme.DARK_GRAY_COLOR);
        announcementPinned.setForeground(new Color(160, 160, 160));
        announcementPinned.setFont(SMALL_FONT);
        JButton postBtn = createButton("Post");
        postBtn.addActionListener(e -> {
            String msg = announcementInput.getText().trim();
            if (msg.isEmpty()) { setStatus("Enter an announcement"); return; }
            if (onCreateAnnouncement != null) onCreateAnnouncement.accept(new Object[]{msg, announcementPinned.isSelected()});
            announcementInput.setText("");
            announcementPinned.setSelected(false);
        });
        addRow.add(announcementPinned);
        addRow.add(postBtn);
        add(addRow);

        add(Box.createVerticalStrut(8));
        add(createSeparator());
        add(Box.createVerticalStrut(6));

        // ══════════════════════════════════
        // Weekly Events
        // ══════════════════════════════════
        add(createSectionTitle("Weekly Events"));
        add(Box.createVerticalStrut(4));

        // Upcoming + running events, refreshed from the platform
        eventsListPanel.setLayout(new BoxLayout(eventsListPanel, BoxLayout.Y_AXIS));
        eventsListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventsListPanel);
        add(Box.createVerticalStrut(6));

        add(createFieldLabel("Event Type"));
        eventTypeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        eventTypeBox.setFont(SMALL_FONT);
        eventTypeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventTypeBox);
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("Metric"));
        eventMetricBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        eventMetricBox.setFont(SMALL_FONT);
        eventMetricBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventMetricBox);
        add(Box.createVerticalStrut(4));

        // Populate metric dropdown based on event type
        populateMetricBox();
        eventTypeBox.addActionListener(e -> populateMetricBox());

        add(createFieldLabel("Custom name (optional)"));
        eventNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        eventNameField.setFont(SMALL_FONT);
        eventNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventNameField);
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("Start \u2014 ET, blank = now (yyyy-MM-dd HH:mm)"));
        eventStartField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        eventStartField.setFont(SMALL_FONT);
        eventStartField.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventStartField);
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("End \u2014 ET, blank = start + 7 days"));
        eventEndField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        eventEndField.setFont(SMALL_FONT);
        eventEndField.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(eventEndField);
        add(Box.createVerticalStrut(6));

        JPanel eventButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        eventButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton startEventBtn = createButton("Start / Schedule");
        startEventBtn.addActionListener(e -> {
            String typeLabel = (String) eventTypeBox.getSelectedItem();
            String metricLabel = (String) eventMetricBox.getSelectedItem();
            if (typeLabel == null || metricLabel == null) return;

            String type = EventMetrics.typeFromLabel(typeLabel);
            String metric = EventMetrics.metricFromDisplayName(metricLabel);
            if (metric == null) return;

            String startText = eventStartField.getText().trim();
            String endText = eventEndField.getText().trim();
            String custom = eventNameField.getText().trim();
            String displayName = custom.isEmpty() ? typeLabel + ": " + metricLabel : custom;

            String when = startText.isEmpty() ? "starting NOW" : "starting " + startText + " ET";
            String until = endText.isEmpty() ? "running 7 days" : "until " + endText + " ET";
            if (confirmAction("Schedule \"" + displayName + "\"?\n" + when + ", " + until + "."))
            {
                if (onStartEvent != null)
                {
                    onStartEvent.accept(new String[]{type, metric, displayName, startText, endText});
                    eventNameField.setText("");
                    eventStartField.setText("");
                    eventEndField.setText("");
                }
            }
        });

        JButton refreshEventsBtn = createButton("Refresh");
        refreshEventsBtn.addActionListener(e -> { if (onLoadEvents != null) onLoadEvents.run(); });

        eventButtons.add(startEventBtn);
        eventButtons.add(refreshEventsBtn);
        add(eventButtons);

        add(Box.createVerticalStrut(8));
        add(createSeparator());
        add(Box.createVerticalStrut(6));

        // ══════════════════════════════════
        // Clan Roster
        // ══════════════════════════════════
        add(createSectionTitle("Clan Roster"));
        add(Box.createVerticalStrut(4));

        JLabel rosterDesc = new JLabel("<html>Sync full member list &amp; ranks to the platform</html>");
        rosterDesc.setFont(SMALL_FONT);
        rosterDesc.setForeground(new Color(150, 150, 150));
        rosterDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(rosterDesc);
        add(Box.createVerticalStrut(4));

        JButton syncRosterBtn = createButton("Sync Roster");
        syncRosterBtn.addActionListener(e -> {
            if (onSyncRoster != null) onSyncRoster.run();
        });
        add(syncRosterBtn);

        add(Box.createVerticalStrut(8));
        add(createSeparator());
        add(Box.createVerticalStrut(6));

        // ══════════════════════════════════
        // Rotate API Key
        // ══════════════════════════════════
        add(createSectionTitle("Rotate API Key"));
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("New API Key (min 6 chars)"));
        newApiKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        newApiKeyField.setFont(SMALL_FONT);
        newApiKeyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(newApiKeyField);
        add(Box.createVerticalStrut(4));

        JButton rotateBtn = createButton("Rotate Key");
        rotateBtn.addActionListener(e -> {
            String newKey = newApiKeyField.getText().trim();
            if (newKey.length() < 6)
            {
                setStatus("API key must be at least 6 characters");
                return;
            }
            if (confirmAction("Rotate API key?\nAll members will need a new clan code."))
            {
                if (onRotateApiKey != null) onRotateApiKey.accept(newKey);
            }
        });
        JPanel rotatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rotatePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rotatePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rotatePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        rotatePanel.add(rotateBtn);
        add(rotatePanel);
        add(Box.createVerticalStrut(2));

        newBoardCodeLabel.setFont(SMALL_ITALIC);
        newBoardCodeLabel.setForeground(new Color(100, 200, 100));
        newBoardCodeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(newBoardCodeLabel);

        add(Box.createVerticalStrut(8));
        add(createSeparator());
        add(Box.createVerticalStrut(6));

        // ══════════════════════════════════
        // Speed Times Moderation
        // ══════════════════════════════════
        add(createSectionTitle("Speed Times Moderation"));
        add(Box.createVerticalStrut(4));

        // Cascading dropdowns: Group → Boss → Size
        JComboBox<String> hsGroupBox = new JComboBox<>();
        JComboBox<String> hsBossBox = new JComboBox<>();
        JComboBox<String> hsSizeBox = new JComboBox<>();

        for (String groupName : BossCategory.getDisplayGroupNames())
        {
            hsGroupBox.addItem(groupName);
        }
        hsGroupBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        hsGroupBox.setFont(SMALL_FONT);
        hsBossBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        hsBossBox.setFont(SMALL_FONT);
        hsSizeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        hsSizeBox.setFont(SMALL_FONT);

        // Wire cascading logic
        hsGroupBox.addActionListener(e -> {
            hsBossBox.removeAllItems();
            String group = (String) hsGroupBox.getSelectedItem();
            if (group == null) return;
            for (String boss : BossCategory.getBossNamesInGroup(group))
            {
                hsBossBox.addItem(boss);
            }
        });

        hsBossBox.addActionListener(e -> {
            hsSizeBox.removeAllItems();
            String group = (String) hsGroupBox.getSelectedItem();
            String boss = (String) hsBossBox.getSelectedItem();
            if (group == null || boss == null) return;
            java.util.List<BossCategory> cats = BossCategory.getCategoriesForBoss(group, boss);
            for (BossCategory cat : cats)
            {
                hsSizeBox.addItem(cat.getSizeLabel());
            }
        });

        // Initialize
        if (hsGroupBox.getItemCount() > 0)
        {
            hsGroupBox.setSelectedIndex(0);
        }

        add(createLabeledField("Group:", hsGroupBox));
        add(Box.createVerticalStrut(2));
        add(createLabeledField("Boss:", hsBossBox));
        add(Box.createVerticalStrut(2));
        add(createLabeledField("Size:", hsSizeBox));
        add(Box.createVerticalStrut(2));

        String[] ranks = {"1", "2", "3"};
        JComboBox<String> hiscoreRankBox = new JComboBox<>(ranks);
        hiscoreRankBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        add(createLabeledField("Rank:", hiscoreRankBox));
        add(Box.createVerticalStrut(4));

        JButton removeHiscoreBtn = createButton("Remove Entry");
        removeHiscoreBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        removeHiscoreBtn.addActionListener(e -> {
            String group = (String) hsGroupBox.getSelectedItem();
            String boss = (String) hsBossBox.getSelectedItem();
            String size = (String) hsSizeBox.getSelectedItem();
            if (group == null || boss == null) return;

            // Find the matching BossCategory
            java.util.List<BossCategory> cats = BossCategory.getCategoriesForBoss(group, boss);
            BossCategory selected = null;
            if (cats.size() == 1)
            {
                selected = cats.get(0);
            }
            else if (size != null)
            {
                for (BossCategory cat : cats)
                {
                    if (cat.getSizeLabel().equals(size)) { selected = cat; break; }
                }
            }
            if (selected == null) return;

            int rank = Integer.parseInt((String) hiscoreRankBox.getSelectedItem());
            if (confirmAction("Remove rank #" + rank + " from " + selected.getDisplayName() + " (" + selected.getSizeLabel() + ")?"))
            {
                if (onRemoveHiscore != null) onRemoveHiscore.accept(new String[]{selected.getKey(), String.valueOf(rank)});
            }
        });
        add(removeHiscoreBtn);
        add(Box.createVerticalStrut(8));

        // ── Status bar ──
        add(createSeparator());
        add(Box.createVerticalStrut(4));
        statusLabel.setFont(SMALL_ITALIC);
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(statusLabel);
    }

    // ── Public methods for populating data ──

    public void setStatus(String text)
    {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    /** @deprecated Clan name is hardcoded to Solus. */
    public void setClanName(String name) { /* no-op */ }

    // ── Callback setters ──

    public void setOnSaveSettings(Consumer<String[]> cb) { this.onSaveSettings = cb; }
    public void setOnLoadSettings(Runnable cb) { this.onLoadSettings = cb; }
    public void setOnRemoveHiscore(Consumer<String[]> cb) { this.onRemoveHiscore = cb; }
    public void setOnRotateApiKey(Consumer<String> cb) { this.onRotateApiKey = cb; }
    public void setOnStartEvent(Consumer<String[]> cb) { this.onStartEvent = cb; }
    public void setOnCancelEvent(Consumer<String> cb) { this.onCancelEvent = cb; }
    public void setOnLoadEvents(Runnable cb) { this.onLoadEvents = cb; }

    /**
     * Render the event calendar. Rows: {id, status, displayName, windowText}.
     * Active rows get an End button, scheduled rows a Cancel button.
     */
    public void setEventsList(List<String[]> rows)
    {
        SwingUtilities.invokeLater(() -> {
            eventsListPanel.removeAll();
            if (rows.isEmpty())
            {
                JLabel none = new JLabel("No running or scheduled events.");
                none.setFont(SMALL_ITALIC);
                none.setForeground(new Color(150, 150, 150));
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                eventsListPanel.add(none);
            }
            for (String[] row : rows)
            {
                final String id = row[0];
                String status = row[1];
                boolean live = "active".equals(status);
                Color chipColor = live ? new Color(76, 175, 80) : new Color(100, 149, 237);

                JPanel card = new JPanel(new BorderLayout(4, 0));
                card.setBackground(new Color(35, 35, 35));
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, chipColor),
                    new EmptyBorder(4, 6, 4, 4)));

                JPanel text = new JPanel();
                text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
                text.setBackground(new Color(35, 35, 35));
                String glyph = row.length > 4 && row[4] != null ? row[4] : "";
                JLabel name = new JLabel("<html><b>" + (live ? "LIVE" : "SCHEDULED") + "</b> \u00b7 " + glyph + row[2] + "</html>");
                name.setFont(SMALL_FONT);
                name.setForeground(chipColor);
                text.add(name);
                JLabel window = new JLabel("<html>" + row[3] + "</html>");
                window.setFont(SMALL_ITALIC);
                window.setForeground(new Color(150, 150, 150));
                text.add(window);
                card.add(text, BorderLayout.CENTER);

                JButton act = createButton(live ? "End" : "Cancel");
                act.addActionListener(e -> {
                    if (confirmAction((live ? "End" : "Cancel") + " \"" + row[2] + "\"?"))
                    {
                        if (onCancelEvent != null) onCancelEvent.accept(id);
                    }
                });
                JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                btnWrap.setBackground(new Color(35, 35, 35));
                btnWrap.add(act);
                card.add(btnWrap, BorderLayout.EAST);

                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 4));
                eventsListPanel.add(card);
                eventsListPanel.add(Box.createVerticalStrut(3));
            }
            eventsListPanel.revalidate();
            eventsListPanel.repaint();
        });
    }
    public void setOnSyncRoster(Runnable cb) { this.onSyncRoster = cb; }
    public void setOnCreateAnnouncement(Consumer<Object[]> cb) { this.onCreateAnnouncement = cb; }
    public void setOnEditAnnouncement(Consumer<String[]> cb) { this.onEditAnnouncement = cb; }
    public void setOnTogglePinAnnouncement(Consumer<Object[]> cb) { this.onTogglePinAnnouncement = cb; }
    public void setOnDeleteAnnouncement(Consumer<String> cb) { this.onDeleteAnnouncement = cb; }

    /** Populate the admin announcements list with edit/pin/delete controls per row. */
    public void setAnnouncementsList(List<PlatformApiService.Announcement> items)
    {
        SwingUtilities.invokeLater(() -> {
            List<PlatformApiService.Announcement> list = items != null ? items : new ArrayList<>();
            announcementsListPanel.removeAll();
            if (list.isEmpty())
            {
                JLabel none = new JLabel("No announcements yet");
                none.setFont(SMALL_ITALIC);
                none.setForeground(new Color(120, 120, 120));
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                announcementsListPanel.add(none);
            }
            else
            {
                for (PlatformApiService.Announcement a : list)
                {
                    announcementsListPanel.add(buildAnnouncementRow(a));
                    announcementsListPanel.add(Box.createVerticalStrut(2));
                }
            }
            announcementsListPanel.revalidate();
            announcementsListPanel.repaint();
        });
    }

    private JPanel buildAnnouncementRow(PlatformApiService.Announcement a)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(new Color(35, 35, 35));
        row.setBorder(new EmptyBorder(3, 5, 3, 5));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        String pin = a.pinned ? "📌 " : ""; // pushpin
        JLabel text = new JLabel("<html>" + pin + escapeHtml(a.message) + "</html>");
        text.setFont(SMALL_FONT);
        text.setForeground(new Color(200, 200, 200));
        row.add(text, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btns.setBackground(new Color(35, 35, 35));

        JButton edit = miniButton("Edit");
        edit.addActionListener(e -> {
            Object updated = JOptionPane.showInputDialog(this, "Edit announcement:", "Edit Announcement",
                JOptionPane.PLAIN_MESSAGE, null, null, a.message);
            if (updated != null && !updated.toString().trim().isEmpty() && onEditAnnouncement != null)
            {
                onEditAnnouncement.accept(new String[]{a.id, updated.toString().trim()});
            }
        });

        JButton pin2 = miniButton(a.pinned ? "Unpin" : "Pin");
        pin2.addActionListener(e -> {
            if (onTogglePinAnnouncement != null) onTogglePinAnnouncement.accept(new Object[]{a.id, !a.pinned});
        });

        JButton del = miniButton("Del");
        del.addActionListener(e -> {
            if (confirmAction("Delete this announcement?") && onDeleteAnnouncement != null) onDeleteAnnouncement.accept(a.id);
        });

        btns.add(edit);
        btns.add(pin2);
        btns.add(del);
        row.add(btns, BorderLayout.EAST);
        return row;
    }

    private JButton miniButton(String text)
    {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        b.setMargin(new Insets(1, 4, 1, 4));
        b.setFocusPainted(false);
        return b;
    }

    private static String escapeHtml(String s)
    {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void setNewBoardCode(String code)
    {
        SwingUtilities.invokeLater(() -> newBoardCodeLabel.setText("New code: " + code));
    }

    /** Legacy hook (config refresh calls this) — the calendar list is the display now. */
    public void setActiveEvent(String type, String displayName, String endTime)
    {
        if (onLoadEvents != null) onLoadEvents.run();
    }

    private void populateMetricBox()
    {
        eventMetricBox.removeAllItems();
        String selected = (String) eventTypeBox.getSelectedItem();
        if (selected == null) return;
        String type = EventMetrics.typeFromLabel(selected);
        if (type == null) return;
        switch (type)
        {
            case "boss":
                for (String name : EventMetrics.getBossDisplayNames()) eventMetricBox.addItem(name);
                break;
            case "skill":
                for (String name : EventMetrics.getSkillDisplayNames()) eventMetricBox.addItem(name);
                break;
            case "gamer":
                for (String name : EventMetrics.getActivityDisplayNames()) eventMetricBox.addItem(name);
                break;
            case "clue":
                for (String name : EventMetrics.getClueDisplayNames()) eventMetricBox.addItem(name);
                break;
        }
    }

    // ── Helpers ──

    private boolean confirmAction(String message)
    {
        return JOptionPane.showConfirmDialog(this, message, "Confirm",
            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private JLabel createSectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(SECTION_FONT);
        label.setForeground(new Color(255, 180, 100));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createFieldLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(new Color(160, 160, 160));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 2, 0));
        return label;
    }

    private JPanel createLabeledField(String labelText, JComponent field)
    {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel label = new JLabel(labelText);
        label.setFont(SMALL_FONT);
        label.setForeground(new Color(200, 200, 200));
        label.setPreferredSize(new Dimension(60, 20));

        panel.add(label, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JButton createButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setFont(SMALL_FONT);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setFocusPainted(false);
        return btn;
    }

    private JSeparator createSeparator()
    {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60, 60, 60));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        return sep;
    }
}
