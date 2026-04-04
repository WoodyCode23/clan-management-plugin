package com.droplogger;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

public class AdminPanel extends JPanel
{
    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font SMALL_FONT = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font SMALL_ITALIC = new Font("Segoe UI", Font.ITALIC, 10);

    private final JLabel statusLabel = new JLabel("Admin ready");

    // ── Shared Settings ──
    private final JTextField clanNameField = new JTextField();
    private final JTextField webhookField = new JTextField();
    private final JTextField womGroupIdField = new JTextField();
    private final JTextArea announcementArea = new JTextArea(3, 20);

    // ── Weekly Events ──
    private final JComboBox<String> eventTypeBox = new JComboBox<>(new String[]{"Boss of the Week", "Skill of the Week"});
    private final JComboBox<String> eventMetricBox = new JComboBox<>();
    private final JLabel activeEventLabel = new JLabel("No active event");

    // ── Rotate API Key ──
    private final JTextField newApiKeyField = new JTextField();
    private final JLabel newBoardCodeLabel = new JLabel(" ");

    // Callbacks
    private Consumer<String[]> onSaveSettings;
    private Runnable onLoadSettings;
    private Consumer<String[]> onRemoveHiscore;
    private Consumer<String> onRotateApiKey;
    private Consumer<String[]> onStartEvent;
    private Runnable onEndEvent;

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

        // ══════════════════════════════════
        // Shared Settings
        // ══════════════════════════════════
        add(createSectionTitle("Shared Settings"));
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("Clan Name"));
        clanNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        clanNameField.setFont(SMALL_FONT);
        clanNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        clanNameField.setToolTipText("Displayed in the plugin UI, chat messages, and Discord posts");
        add(clanNameField);
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("Discord Webhook URL"));
        webhookField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        webhookField.setFont(SMALL_FONT);
        webhookField.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(webhookField);
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("WOM Group ID"));
        womGroupIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        womGroupIdField.setFont(SMALL_FONT);
        womGroupIdField.setAlignmentX(Component.LEFT_ALIGNMENT);
        womGroupIdField.setToolTipText("Wise Old Man group ID for XP tracking (found in your WOM group URL)");
        add(womGroupIdField);
        add(Box.createVerticalStrut(4));

        add(createFieldLabel("Announcement (visible to all members)"));
        announcementArea.setFont(SMALL_FONT);
        announcementArea.setLineWrap(true);
        announcementArea.setWrapStyleWord(true);
        announcementArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane announcementScroll = new JScrollPane(announcementArea);
        announcementScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        announcementScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        announcementScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        add(announcementScroll);
        add(Box.createVerticalStrut(6));

        JPanel settingsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        settingsButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        settingsButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton loadSettingsBtn = createButton("Load");
        loadSettingsBtn.addActionListener(e -> {
            if (onLoadSettings != null) onLoadSettings.run();
        });

        JButton saveSettingsBtn = createButton("Save");
        saveSettingsBtn.addActionListener(e -> {
            String clanNameVal = clanNameField.getText().trim();
            String webhook = webhookField.getText().trim();
            String womId = womGroupIdField.getText().trim();
            String announcement = announcementArea.getText().trim();
            if (onSaveSettings != null)
            {
                onSaveSettings.accept(new String[]{clanNameVal, webhook, womId, announcement});
            }
        });

        settingsButtons.add(loadSettingsBtn);
        settingsButtons.add(saveSettingsBtn);
        add(settingsButtons);

        add(Box.createVerticalStrut(8));
        add(createSeparator());
        add(Box.createVerticalStrut(6));

        // ══════════════════════════════════
        // Weekly Events
        // ══════════════════════════════════
        add(createSectionTitle("Weekly Events"));
        add(Box.createVerticalStrut(4));

        activeEventLabel.setFont(SMALL_ITALIC);
        activeEventLabel.setForeground(new Color(150, 150, 150));
        activeEventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(activeEventLabel);
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

        JPanel eventButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        eventButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton startEventBtn = createButton("Start Event");
        startEventBtn.addActionListener(e -> {
            String typeLabel = (String) eventTypeBox.getSelectedItem();
            String displayName = (String) eventMetricBox.getSelectedItem();
            if (typeLabel == null || displayName == null) return;

            String type = typeLabel.startsWith("Boss") ? "boss" : "skill";
            String metric = EventMetrics.metricFromDisplayName(displayName);
            if (metric == null) return;

            if (confirmAction("Start " + typeLabel + ": " + displayName + "?\nThis will run for 7 days."))
            {
                if (onStartEvent != null) onStartEvent.accept(new String[]{type, metric, displayName});
            }
        });

        JButton endEventBtn = createButton("End Event");
        endEventBtn.addActionListener(e -> {
            if (confirmAction("End the current event?"))
            {
                if (onEndEvent != null) onEndEvent.run();
            }
        });

        eventButtons.add(startEventBtn);
        eventButtons.add(endEventBtn);
        add(eventButtons);

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
        // Hiscore Moderation
        // ══════════════════════════════════
        add(createSectionTitle("Hiscore Moderation"));
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

    public void setClanName(String name)
    {
        SwingUtilities.invokeLater(() -> clanNameField.setText(name != null ? name : ""));
    }

    public void setWebhookUrl(String url)
    {
        SwingUtilities.invokeLater(() -> webhookField.setText(url != null ? url : ""));
    }

    public void setWomGroupId(String id)
    {
        SwingUtilities.invokeLater(() -> womGroupIdField.setText(id != null ? id : ""));
    }

    public void setAnnouncement(String text)
    {
        SwingUtilities.invokeLater(() -> announcementArea.setText(text != null ? text : ""));
    }

    // ── Callback setters ──

    public void setOnSaveSettings(Consumer<String[]> cb) { this.onSaveSettings = cb; }
    public void setOnLoadSettings(Runnable cb) { this.onLoadSettings = cb; }
    public void setOnRemoveHiscore(Consumer<String[]> cb) { this.onRemoveHiscore = cb; }
    public void setOnRotateApiKey(Consumer<String> cb) { this.onRotateApiKey = cb; }
    public void setOnStartEvent(Consumer<String[]> cb) { this.onStartEvent = cb; }
    public void setOnEndEvent(Runnable cb) { this.onEndEvent = cb; }

    public void setNewBoardCode(String code)
    {
        SwingUtilities.invokeLater(() -> newBoardCodeLabel.setText("New code: " + code));
    }

    public void setActiveEvent(String type, String displayName, String endTime)
    {
        SwingUtilities.invokeLater(() -> {
            if (type == null || type.isEmpty())
            {
                activeEventLabel.setText("No active event");
                activeEventLabel.setForeground(new Color(150, 150, 150));
            }
            else
            {
                String typeLabel = "boss".equals(type) ? "Boss" : "Skill";
                activeEventLabel.setText("Active: " + typeLabel + " — " + displayName + " (ends " + endTime.replace("T", " ") + " ET)");
                activeEventLabel.setForeground("boss".equals(type) ? new Color(231, 76, 60) : new Color(46, 204, 113));
            }
        });
    }

    private void populateMetricBox()
    {
        eventMetricBox.removeAllItems();
        String selected = (String) eventTypeBox.getSelectedItem();
        if (selected != null && selected.startsWith("Boss"))
        {
            for (String name : EventMetrics.getBossDisplayNames()) eventMetricBox.addItem(name);
        }
        else
        {
            for (String name : EventMetrics.getSkillDisplayNames()) eventMetricBox.addItem(name);
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
