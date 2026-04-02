package com.droplogger;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
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
    private final JTextField clanDropLogUrlField = new JTextField();
    private final JTextArea announcementArea = new JTextArea(3, 20);
    private final JTextField bingoStartField = new JTextField();
    private final JTextField bingoEndField = new JTextField();

    // ── Team Drops ──
    private final JPanel dropsListPanel = new JPanel();
    private final JComboBox<String> teamDropsBox = new JComboBox<>();

    // ── Bingo sections container ──
    private final JPanel bingoSectionsPanel = new JPanel();
    private final JComboBox<String> bountyWinnerBox = new JComboBox<>();

    // ── Rotate API Key ──
    private final JTextField newApiKeyField = new JTextField();
    private final JLabel newBoardCodeLabel = new JLabel(" ");

    // Callbacks
    private Consumer<String[]> onSaveSettings;
    private Runnable onLoadSettings;
    private Consumer<String> onLoadTeamDrops;
    private Consumer<String[]> onRemoveDrop;
    private Consumer<String[]> onSetBountyWinner;
    private Consumer<String[]> onRemoveHiscore;
    private Consumer<String> onRotateApiKey;

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
        // Shared Settings (webhook + announcements)
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

        add(createFieldLabel("Clan Drop Log / Hiscores URL"));
        clanDropLogUrlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        clanDropLogUrlField.setFont(SMALL_FONT);
        clanDropLogUrlField.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(clanDropLogUrlField);
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
            String clanLogUrl = clanDropLogUrlField.getText().trim();
            String announcement = announcementArea.getText().trim();
            String bingoStart = bingoStartField.getText().trim();
            String bingoEnd = bingoEndField.getText().trim();
            if (onSaveSettings != null)
            {
                // Send clanLogUrl for both hiscoreApiUrl and clanDropLogUrl (unified)
                onSaveSettings.accept(new String[]{webhook, announcement, clanLogUrl, clanLogUrl, bingoStart, bingoEnd, clanNameVal});
            }
        });

        settingsButtons.add(loadSettingsBtn);
        settingsButtons.add(saveSettingsBtn);
        add(settingsButtons);

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
            if (confirmAction("Rotate API key on all services?\nAll members will need a new board code."))
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
        // Events — collapsible section for event modules
        // ══════════════════════════════════
        JLabel eventsHeader = createSectionTitle("Events");
        add(eventsHeader);
        add(Box.createVerticalStrut(6));

        // ── Bingo event (collapsible) ──
        JPanel bingoToggleRow = new JPanel(new BorderLayout());
        bingoToggleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bingoToggleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoToggleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        bingoToggleRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel bingoToggleLabel = new JLabel("\u25B6 Bingo");
        bingoToggleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        bingoToggleLabel.setForeground(new Color(180, 140, 255));
        bingoToggleRow.add(bingoToggleLabel, BorderLayout.WEST);
        add(bingoToggleRow);
        add(Box.createVerticalStrut(4));

        // Bingo content panel (starts collapsed)
        bingoSectionsPanel.setLayout(new BoxLayout(bingoSectionsPanel, BoxLayout.Y_AXIS));
        bingoSectionsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bingoSectionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoSectionsPanel.setVisible(false);

        // Toggle bingo section on click
        bingoToggleRow.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                boolean show = !bingoSectionsPanel.isVisible();
                bingoSectionsPanel.setVisible(show);
                bingoToggleLabel.setText((show ? "\u25BC " : "\u25B6 ") + "Bingo");
                revalidate();
                repaint();
            }
        });

        // ── Bingo Schedule ──
        bingoSectionsPanel.add(createFieldLabel("Start Date (YYYY-MM-DD)"));
        bingoStartField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        bingoStartField.setFont(SMALL_FONT);
        bingoStartField.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoStartField.setToolTipText("Bingo tab shows 7 days before this date");
        bingoSectionsPanel.add(bingoStartField);
        bingoSectionsPanel.add(Box.createVerticalStrut(4));

        bingoSectionsPanel.add(createFieldLabel("End Date (YYYY-MM-DD)"));
        bingoEndField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        bingoEndField.setFont(SMALL_FONT);
        bingoEndField.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoEndField.setToolTipText("Bingo tab hides after this date");
        bingoSectionsPanel.add(bingoEndField);
        bingoSectionsPanel.add(Box.createVerticalStrut(6));

        // ── Team Drop Management ──
        bingoSectionsPanel.add(createSectionTitle("Team Drops"));
        bingoSectionsPanel.add(Box.createVerticalStrut(4));

        bingoSectionsPanel.add(createFieldLabel("Select a team to view recent drops"));

        // Combo box for team selection (populated dynamically via setTeams)
        teamDropsBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        teamDropsBox.setFont(SMALL_FONT);
        teamDropsBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoSectionsPanel.add(teamDropsBox);
        bingoSectionsPanel.add(Box.createVerticalStrut(4));

        JButton loadDropsBtn = createButton("Load Drops");
        loadDropsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loadDropsBtn.addActionListener(e -> {
            String selected = (String) teamDropsBox.getSelectedItem();
            if (selected != null && !selected.isEmpty() && onLoadTeamDrops != null)
            {
                onLoadTeamDrops.accept(selected);
            }
        });
        bingoSectionsPanel.add(loadDropsBtn);
        bingoSectionsPanel.add(Box.createVerticalStrut(4));

        dropsListPanel.setLayout(new BoxLayout(dropsListPanel, BoxLayout.Y_AXIS));
        dropsListPanel.setBackground(new Color(25, 25, 25));
        dropsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bingoSectionsPanel.add(dropsListPanel);

        bingoSectionsPanel.add(Box.createVerticalStrut(8));
        bingoSectionsPanel.add(createSeparator());
        bingoSectionsPanel.add(Box.createVerticalStrut(6));

        // ── Bounty Management ──
        bingoSectionsPanel.add(createSectionTitle("Bounty Management"));
        bingoSectionsPanel.add(Box.createVerticalStrut(4));

        String[] bountyNums = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
        JComboBox<String> bountyNumBox = new JComboBox<>(bountyNums);
        bountyNumBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        bountyWinnerBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JTextField bountyDescField = new JTextField();
        bountyDescField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        bountyDescField.setFont(SMALL_FONT);

        bingoSectionsPanel.add(createLabeledField("Bounty #:", bountyNumBox));
        bingoSectionsPanel.add(Box.createVerticalStrut(2));
        bingoSectionsPanel.add(createLabeledField("Winner:", bountyWinnerBox));
        bingoSectionsPanel.add(Box.createVerticalStrut(2));
        bingoSectionsPanel.add(createLabeledField("Desc:", bountyDescField));
        bingoSectionsPanel.add(Box.createVerticalStrut(4));

        JButton setBountyBtn = createButton("Set Winner");
        setBountyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        setBountyBtn.addActionListener(e -> {
            String num = (String) bountyNumBox.getSelectedItem();
            String winner = (String) bountyWinnerBox.getSelectedItem();
            String desc = bountyDescField.getText().trim();
            if (confirmAction("Set Bounty #" + num + " winner to " + winner + "?"))
            {
                if (onSetBountyWinner != null) onSetBountyWinner.accept(new String[]{num, winner, desc});
            }
        });
        bingoSectionsPanel.add(setBountyBtn);

        bingoSectionsPanel.add(Box.createVerticalStrut(8));
        bingoSectionsPanel.add(createSeparator());
        bingoSectionsPanel.add(Box.createVerticalStrut(6));

        add(bingoSectionsPanel);

        // ══════════════════════════════════
        // Hiscore Moderation (always visible)
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

    public void setAnnouncement(String text)
    {
        SwingUtilities.invokeLater(() -> announcementArea.setText(text != null ? text : ""));
    }

    public void setHiscoreApiUrl(String url)
    {
        // Legacy — if clanDropLogUrl isn't set yet, use hiscoreApiUrl as fallback
        SwingUtilities.invokeLater(() ->
        {
            if (clanDropLogUrlField.getText().isEmpty() && url != null && !url.isEmpty())
            {
                clanDropLogUrlField.setText(url);
            }
        });
    }

    public void setClanDropLogUrl(String url)
    {
        SwingUtilities.invokeLater(() -> clanDropLogUrlField.setText(url != null ? url : ""));
    }

    public void setBingoStartDate(String date)
    {
        SwingUtilities.invokeLater(() -> bingoStartField.setText(date != null ? date : ""));
    }

    public void setBingoEndDate(String date)
    {
        SwingUtilities.invokeLater(() -> bingoEndField.setText(date != null ? date : ""));
    }

    /**
     * Set the list of team codes for the team drops and bounty winner dropdowns.
     * Call this to dynamically populate teams (e.g., from TeamCode enum or server data).
     */
    public void setTeams(String[] teamCodes)
    {
        SwingUtilities.invokeLater(() -> {
            teamDropsBox.removeAllItems();
            bountyWinnerBox.removeAllItems();
            for (String code : teamCodes)
            {
                teamDropsBox.addItem(code);
                bountyWinnerBox.addItem(code);
            }
        });
    }

    /**
     * Show or hide the bingo-specific admin sections (Team Drops, Bounty Management).
     */
    public void setBingoSectionsVisible(boolean visible)
    {
        SwingUtilities.invokeLater(() -> {
            bingoSectionsPanel.setVisible(visible);
            revalidate();
            repaint();
        });
    }

    public void showTeamDrops(String teamCode, List<BingoModels.TeamDrop> drops)
    {
        SwingUtilities.invokeLater(() -> {
            dropsListPanel.removeAll();

            if (drops == null || drops.isEmpty())
            {
                JLabel empty = new JLabel("No drops for " + teamCode);
                empty.setFont(SMALL_ITALIC);
                empty.setForeground(new Color(100, 100, 100));
                empty.setBorder(new EmptyBorder(6, 6, 6, 6));
                dropsListPanel.add(empty);
            }
            else
            {
                JLabel teamTitle = new JLabel(teamCode + " — " + drops.size() + " drops");
                teamTitle.setFont(SECTION_FONT);
                teamTitle.setForeground(new Color(100, 180, 255));
                teamTitle.setBorder(new EmptyBorder(4, 6, 4, 6));
                teamTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
                dropsListPanel.add(teamTitle);

                for (BingoModels.TeamDrop drop : drops)
                {
                    dropsListPanel.add(createDropRow(teamCode, drop));
                }
            }

            dropsListPanel.revalidate();
            dropsListPanel.repaint();
        });
    }

    private JPanel createDropRow(String teamCode, BingoModels.TeamDrop drop)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(new Color(25, 25, 25));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)),
            new EmptyBorder(4, 6, 4, 4)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        // Left side: drop info
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(new Color(25, 25, 25));

        JLabel itemLabel = new JLabel(drop.getDropName());
        itemLabel.setFont(LABEL_FONT);
        itemLabel.setForeground(new Color(220, 220, 220));
        info.add(itemLabel);

        String detail = drop.getRsn();
        if (drop.getDate() != null && !drop.getDate().isEmpty())
        {
            detail += " — " + drop.getDate();
        }
        if (drop.getTileCode() != null && !drop.getTileCode().isEmpty())
        {
            detail += " [" + drop.getTileCode() + "]";
        }
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(SMALL_FONT);
        detailLabel.setForeground(new Color(140, 140, 140));
        info.add(detailLabel);

        row.add(info, BorderLayout.CENTER);

        // Right side: remove button
        JLabel removeBtn = new JLabel("\u2715");
        removeBtn.setFont(removeBtn.getFont().deriveFont(12f));
        removeBtn.setForeground(new Color(150, 60, 60));
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.setBorder(new EmptyBorder(0, 4, 0, 4));
        removeBtn.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                if (confirmAction("Remove " + drop.getDropName() + " from " + drop.getRsn() + "?"))
                {
                    if (onRemoveDrop != null)
                    {
                        onRemoveDrop.accept(new String[]{
                            teamCode, drop.getRsn(), drop.getDropName(),
                            drop.getTileCode(), drop.getDate()
                        });
                    }
                }
            }
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) { removeBtn.setForeground(new Color(220, 60, 60)); }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) { removeBtn.setForeground(new Color(150, 60, 60)); }
        });
        row.add(removeBtn, BorderLayout.EAST);

        return row;
    }

    // ── Callback setters ──

    public void setOnSaveSettings(Consumer<String[]> cb) { this.onSaveSettings = cb; }
    public void setOnLoadSettings(Runnable cb) { this.onLoadSettings = cb; }
    public void setOnLoadTeamDrops(Consumer<String> cb) { this.onLoadTeamDrops = cb; }
    public void setOnRemoveDrop(Consumer<String[]> cb) { this.onRemoveDrop = cb; }
    public void setOnSetBountyWinner(Consumer<String[]> cb) { this.onSetBountyWinner = cb; }
    public void setOnRemoveHiscore(Consumer<String[]> cb) { this.onRemoveHiscore = cb; }
    public void setOnRotateApiKey(Consumer<String> cb) { this.onRotateApiKey = cb; }

    public void setNewBoardCode(String code)
    {
        SwingUtilities.invokeLater(() -> newBoardCodeLabel.setText("New code: " + code));
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
