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
    private final JComboBox<String> eventTypeBox = new JComboBox<>(new String[]{"Boss of the Week", "Skill of the Week", "Gamer of the Week", "Clue Hunter of the Week"});
    private final JComboBox<String> eventMetricBox = new JComboBox<>();
    private final JLabel activeEventLabel = new JLabel("No active event");

    // ── Rotate API Key ──
    private final JTextField newApiKeyField = new JTextField();
    private final JLabel newBoardCodeLabel = new JLabel(" ");

    // ── Bingo Admin ──
    private JPanel bingoAdminSection;

    // ── Bingo Host ──
    private JPanel bingoHostSection;

    // Callbacks
    private Consumer<String[]> onSaveSettings;
    private Runnable onLoadSettings;
    private Consumer<String[]> onRemoveHiscore;
    private Consumer<String> onRotateApiKey;
    private Consumer<String[]> onStartEvent;
    private Runnable onEndEvent;
    private Consumer<String[]> onBingoAssignRoster;
    private Consumer<String> onBingoRemoveRoster;
    private Consumer<String[]> onBingoAdjustProgress;
    private Consumer<String[]> onBingoAddBounty;
    private Consumer<String[]> onBingoSetWinner;
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

            String type = EventMetrics.typeFromLabel(typeLabel);
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
        // Clan Roster
        // ══════════════════════════════════
        add(createSectionTitle("Clan Roster"));
        add(Box.createVerticalStrut(4));

        JLabel rosterDesc = new JLabel("Sync full member list & ranks to the platform");
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

        // ══════════════════════════════════
        // Bingo Admin (hidden until bingo keys configured)
        // ══════════════════════════════════
        bingoAdminSection = new JPanel();
        bingoAdminSection.setLayout(new BoxLayout(bingoAdminSection, BoxLayout.Y_AXIS));
        bingoAdminSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bingoAdminSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoAdminSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        {
            bingoAdminSection.add(createSeparator());
            bingoAdminSection.add(Box.createVerticalStrut(6));

            JLabel bingoAdminTitle = new JLabel("Bingo Admin");
            bingoAdminTitle.setFont(SECTION_FONT);
            bingoAdminTitle.setForeground(new Color(255, 180, 100));
            bingoAdminTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            bingoAdminSection.add(bingoAdminTitle);
            bingoAdminSection.add(Box.createVerticalStrut(4));

            // ── Roster Management ──
            JTextField bingoRsnField = new JTextField();
            bingoRsnField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bingoRsnField.setFont(SMALL_FONT);
            bingoRsnField.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField bingoRosterTeamField = new JTextField();
            bingoRosterTeamField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bingoRosterTeamField.setFont(SMALL_FONT);
            bingoRosterTeamField.setAlignmentX(Component.LEFT_ALIGNMENT);

            bingoAdminSection.add(createFieldLabel("RSN"));
            bingoAdminSection.add(bingoRsnField);
            bingoAdminSection.add(Box.createVerticalStrut(4));

            bingoAdminSection.add(createFieldLabel("Team Code"));
            bingoAdminSection.add(bingoRosterTeamField);
            bingoAdminSection.add(Box.createVerticalStrut(4));

            JPanel rosterButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            rosterButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
            rosterButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
            rosterButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JButton assignRosterBtn = createButton("Assign to Team");
            assignRosterBtn.addActionListener(e -> {
                String rsn = bingoRsnField.getText().trim();
                String team = bingoRosterTeamField.getText().trim();
                if (onBingoAssignRoster != null) onBingoAssignRoster.accept(new String[]{rsn, team});
            });

            JButton removeRosterBtn = createButton("Remove from Roster");
            removeRosterBtn.addActionListener(e -> {
                String rsn = bingoRsnField.getText().trim();
                if (onBingoRemoveRoster != null) onBingoRemoveRoster.accept(rsn);
            });

            rosterButtons.add(assignRosterBtn);
            rosterButtons.add(removeRosterBtn);
            bingoAdminSection.add(rosterButtons);
            bingoAdminSection.add(Box.createVerticalStrut(6));

            bingoAdminSection.add(createSeparator());
            bingoAdminSection.add(Box.createVerticalStrut(6));

            // ── Progress Adjustment ──
            JTextField bingoProgressTeamField = new JTextField();
            bingoProgressTeamField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bingoProgressTeamField.setFont(SMALL_FONT);
            bingoProgressTeamField.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField bingoTileCodeField = new JTextField();
            bingoTileCodeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bingoTileCodeField.setFont(SMALL_FONT);
            bingoTileCodeField.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField bingoProgressPointsField = new JTextField();
            bingoProgressPointsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bingoProgressPointsField.setFont(SMALL_FONT);
            bingoProgressPointsField.setAlignmentX(Component.LEFT_ALIGNMENT);

            bingoAdminSection.add(createFieldLabel("Team Code"));
            bingoAdminSection.add(bingoProgressTeamField);
            bingoAdminSection.add(Box.createVerticalStrut(4));

            bingoAdminSection.add(createFieldLabel("Tile Code"));
            bingoAdminSection.add(bingoTileCodeField);
            bingoAdminSection.add(Box.createVerticalStrut(4));

            bingoAdminSection.add(createFieldLabel("Points"));
            bingoAdminSection.add(bingoProgressPointsField);
            bingoAdminSection.add(Box.createVerticalStrut(4));

            JPanel adjustProgressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            adjustProgressPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            adjustProgressPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            adjustProgressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JButton adjustProgressBtn = createButton("Adjust Progress");
            adjustProgressBtn.addActionListener(e -> {
                String team = bingoProgressTeamField.getText().trim();
                String tileCode = bingoTileCodeField.getText().trim();
                String points = bingoProgressPointsField.getText().trim();
                if (onBingoAdjustProgress != null) onBingoAdjustProgress.accept(new String[]{team, tileCode, points});
            });

            adjustProgressPanel.add(adjustProgressBtn);
            bingoAdminSection.add(adjustProgressPanel);
            bingoAdminSection.add(Box.createVerticalStrut(8));
        }

        bingoAdminSection.setVisible(false);
        add(bingoAdminSection);

        // ══════════════════════════════════
        // Bingo Host (hidden until bingo keys configured)
        // ══════════════════════════════════
        bingoHostSection = new JPanel();
        bingoHostSection.setLayout(new BoxLayout(bingoHostSection, BoxLayout.Y_AXIS));
        bingoHostSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bingoHostSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoHostSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        {
            bingoHostSection.add(createSeparator());
            bingoHostSection.add(Box.createVerticalStrut(6));

            JLabel bingoHostTitle = new JLabel("Bingo Host");
            bingoHostTitle.setFont(SECTION_FONT);
            bingoHostTitle.setForeground(new Color(255, 100, 100));
            bingoHostTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            bingoHostSection.add(bingoHostTitle);
            bingoHostSection.add(Box.createVerticalStrut(4));

            // ── Add Bounty ──
            JTextField bountyNumberField = new JTextField();
            bountyNumberField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bountyNumberField.setFont(SMALL_FONT);
            bountyNumberField.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField bountyDescField = new JTextField();
            bountyDescField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bountyDescField.setFont(SMALL_FONT);
            bountyDescField.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField bountyReleaseTimeField = new JTextField();
            bountyReleaseTimeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bountyReleaseTimeField.setFont(SMALL_FONT);
            bountyReleaseTimeField.setAlignmentX(Component.LEFT_ALIGNMENT);
            bountyReleaseTimeField.setToolTipText("e.g. 2026-04-15T18:00");

            JTextField bountyPointsField = new JTextField();
            bountyPointsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            bountyPointsField.setFont(SMALL_FONT);
            bountyPointsField.setAlignmentX(Component.LEFT_ALIGNMENT);

            bingoHostSection.add(createFieldLabel("Bounty Number"));
            bingoHostSection.add(bountyNumberField);
            bingoHostSection.add(Box.createVerticalStrut(4));

            bingoHostSection.add(createFieldLabel("Description"));
            bingoHostSection.add(bountyDescField);
            bingoHostSection.add(Box.createVerticalStrut(4));

            bingoHostSection.add(createFieldLabel("Release Time (e.g. 2026-04-15T18:00)"));
            bingoHostSection.add(bountyReleaseTimeField);
            bingoHostSection.add(Box.createVerticalStrut(4));

            bingoHostSection.add(createFieldLabel("Points"));
            bingoHostSection.add(bountyPointsField);
            bingoHostSection.add(Box.createVerticalStrut(4));

            JPanel addBountyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            addBountyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            addBountyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            addBountyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JButton addBountyBtn = createButton("Add Bounty");
            addBountyBtn.addActionListener(e -> {
                String number = bountyNumberField.getText().trim();
                String description = bountyDescField.getText().trim();
                String releaseTime = bountyReleaseTimeField.getText().trim();
                String points = bountyPointsField.getText().trim();
                if (onBingoAddBounty != null) onBingoAddBounty.accept(new String[]{number, description, releaseTime, points});
            });

            addBountyPanel.add(addBountyBtn);
            bingoHostSection.add(addBountyPanel);
            bingoHostSection.add(Box.createVerticalStrut(6));

            bingoHostSection.add(createSeparator());
            bingoHostSection.add(Box.createVerticalStrut(6));

            // ── Set Winner ──
            JTextField winnerBountyNumberField = new JTextField();
            winnerBountyNumberField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            winnerBountyNumberField.setFont(SMALL_FONT);
            winnerBountyNumberField.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField winnerField = new JTextField();
            winnerField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            winnerField.setFont(SMALL_FONT);
            winnerField.setAlignmentX(Component.LEFT_ALIGNMENT);

            bingoHostSection.add(createFieldLabel("Bounty Number"));
            bingoHostSection.add(winnerBountyNumberField);
            bingoHostSection.add(Box.createVerticalStrut(4));

            bingoHostSection.add(createFieldLabel("Winner (Team Code or RSN)"));
            bingoHostSection.add(winnerField);
            bingoHostSection.add(Box.createVerticalStrut(4));

            JPanel setWinnerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setWinnerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            setWinnerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            setWinnerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JButton setWinnerBtn = createButton("Set Winner");
            setWinnerBtn.addActionListener(e -> {
                String number = winnerBountyNumberField.getText().trim();
                String winner = winnerField.getText().trim();
                if (onBingoSetWinner != null) onBingoSetWinner.accept(new String[]{number, winner});
            });

            setWinnerPanel.add(setWinnerBtn);
            bingoHostSection.add(setWinnerPanel);
            bingoHostSection.add(Box.createVerticalStrut(8));
        }

        bingoHostSection.setVisible(false);
        add(bingoHostSection);

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
    public void setOnBingoAssignRoster(Consumer<String[]> cb) { this.onBingoAssignRoster = cb; }
    public void setOnBingoRemoveRoster(Consumer<String> cb) { this.onBingoRemoveRoster = cb; }
    public void setOnBingoAdjustProgress(Consumer<String[]> cb) { this.onBingoAdjustProgress = cb; }
    public void setOnBingoAddBounty(Consumer<String[]> cb) { this.onBingoAddBounty = cb; }
    public void setOnBingoSetWinner(Consumer<String[]> cb) { this.onBingoSetWinner = cb; }
    public void setOnSyncRoster(Runnable cb) { this.onSyncRoster = cb; }

    public void showBingoAdminSection(boolean visible)
    {
        SwingUtilities.invokeLater(() -> bingoAdminSection.setVisible(visible));
    }

    public void showBingoHostSection(boolean visible)
    {
        SwingUtilities.invokeLater(() -> bingoHostSection.setVisible(visible));
    }

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
