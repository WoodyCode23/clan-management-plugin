package com.droplogger;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BingoPanel extends JPanel
{
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_ITALIC = new Font("Segoe UI", Font.ITALIC, 10);
    private static final Color ACCENT_GOLD = new Color(0xFF, 0xD7, 0x00);
    private static final NumberFormat NUM_FMT = NumberFormat.getNumberInstance(Locale.US);

    // Tile colors by completion
    private static final Color TILE_GRAY = new Color(60, 60, 60);
    private static final Color TILE_YELLOW = new Color(180, 160, 50);
    private static final Color TILE_GREEN = new Color(50, 150, 60);
    private static final Color TILE_BLACK = new Color(20, 20, 20);

    // Sections
    private final JLabel eventNameLabel = new JLabel("Bingo");
    private final JLabel countdownLabel = new JLabel("");
    private final JLabel teamLabel = new JLabel("");
    private final JPanel gridPanel = new JPanel();
    private final JPanel tileDetailPanel = new JPanel();
    private final JPanel teamStandingsPanel = new JPanel();
    private final JPanel individualStandingsPanel = new JPanel();
    private final JPanel bountyPanel = new JPanel();
    private final JPanel dropLogPanel = new JPanel();

    private BingoConfig config;
    private Map<String, Double> teamProgress;
    private String selectedTileCode;

    public BingoPanel()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Header
        eventNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        eventNameLabel.setForeground(ACCENT_GOLD);
        eventNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        eventNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        eventNameLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        content.add(eventNameLabel);
        content.add(Box.createVerticalStrut(2));

        countdownLabel.setFont(FONT_SMALL);
        countdownLabel.setForeground(new Color(170, 170, 170));
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        countdownLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        content.add(countdownLabel);
        content.add(Box.createVerticalStrut(2));

        teamLabel.setFont(FONT_BOLD);
        teamLabel.setForeground(new Color(46, 204, 113));
        teamLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        teamLabel.setHorizontalAlignment(SwingConstants.CENTER);
        teamLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        content.add(teamLabel);
        content.add(Box.createVerticalStrut(8));

        // Grid
        gridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        gridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(gridPanel);
        content.add(Box.createVerticalStrut(4));

        // Tile detail (hidden initially)
        tileDetailPanel.setLayout(new BoxLayout(tileDetailPanel, BoxLayout.Y_AXIS));
        tileDetailPanel.setBackground(new Color(40, 40, 40));
        tileDetailPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        tileDetailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.setVisible(false);
        tileDetailPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        content.add(tileDetailPanel);
        content.add(Box.createVerticalStrut(8));

        // Team standings
        content.add(createSectionLabel("Team Standings"));
        content.add(Box.createVerticalStrut(4));
        teamStandingsPanel.setLayout(new BoxLayout(teamStandingsPanel, BoxLayout.Y_AXIS));
        teamStandingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        teamStandingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(teamStandingsPanel);
        content.add(Box.createVerticalStrut(8));

        // Individual standings
        content.add(createSectionLabel("Individual Standings"));
        content.add(Box.createVerticalStrut(4));
        individualStandingsPanel.setLayout(new BoxLayout(individualStandingsPanel, BoxLayout.Y_AXIS));
        individualStandingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        individualStandingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(individualStandingsPanel);
        content.add(Box.createVerticalStrut(8));

        // Bounties
        content.add(createSectionLabel("Bounties"));
        content.add(Box.createVerticalStrut(4));
        bountyPanel.setLayout(new BoxLayout(bountyPanel, BoxLayout.Y_AXIS));
        bountyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bountyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(bountyPanel);
        content.add(Box.createVerticalStrut(8));

        // Drop log
        content.add(createSectionLabel("Recent Drops"));
        content.add(Box.createVerticalStrut(4));
        dropLogPanel.setLayout(new BoxLayout(dropLogPanel, BoxLayout.Y_AXIS));
        dropLogPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropLogPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(dropLogPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateConfig(BingoConfig config, String playerTeamCode, String playerTeamName)
    {
        this.config = config;
        SwingUtilities.invokeLater(() -> {
            eventNameLabel.setText(config.getEventName());
            teamLabel.setText(playerTeamName != null ? playerTeamName : "No team assigned");
            rebuildGrid();
        });
    }

    public void updateCountdown(String text)
    {
        SwingUtilities.invokeLater(() -> countdownLabel.setText(text != null ? text : ""));
    }

    public void updateTeamProgress(Map<String, Double> progress)
    {
        this.teamProgress = progress;
        SwingUtilities.invokeLater(this::refreshGridColors);
    }

    public void updateStandings(BingoStandings standings, String playerRsn, String playerTeamCode)
    {
        SwingUtilities.invokeLater(() -> {
            // Team standings
            teamStandingsPanel.removeAll();
            if (standings.getTeamStandings() != null)
            {
                for (BingoTeam team : standings.getTeamStandings())
                {
                    boolean isMyTeam = team.getCode().equals(playerTeamCode);
                    String text = "#" + team.getRank() + " " + team.getName() + " — " +
                        NUM_FMT.format(team.getTotalPoints()) + " pts";
                    if (team.getBountyBonus() > 0)
                    {
                        text += " (+" + NUM_FMT.format(team.getBountyBonus()) + " bounty)";
                    }
                    JLabel row = new JLabel(text);
                    row.setFont(isMyTeam ? FONT_BOLD : FONT);
                    row.setForeground(isMyTeam ? new Color(46, 204, 113) : new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    teamStandingsPanel.add(row);
                    teamStandingsPanel.add(Box.createVerticalStrut(2));
                }
            }
            teamStandingsPanel.revalidate();
            teamStandingsPanel.repaint();

            // Individual standings
            individualStandingsPanel.removeAll();
            if (standings.getIndividualStandings() != null)
            {
                int shown = Math.min(20, standings.getIndividualStandings().size());
                for (int i = 0; i < shown; i++)
                {
                    BingoStandings.PlayerStanding ps = standings.getIndividualStandings().get(i);
                    boolean isMe = ps.getRsn().equalsIgnoreCase(playerRsn);
                    String text = "#" + ps.getRank() + " " + ps.getRsn() + " — " +
                        NUM_FMT.format(ps.getPoints()) + " pts";
                    JLabel row = new JLabel(text);
                    row.setFont(isMe ? FONT_BOLD : FONT);
                    row.setForeground(isMe ? new Color(46, 204, 113) : new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    individualStandingsPanel.add(row);
                    individualStandingsPanel.add(Box.createVerticalStrut(2));
                }
            }
            individualStandingsPanel.revalidate();
            individualStandingsPanel.repaint();
        });
    }

    public void updateBounties(List<BingoBounty> bounties, String nextCountdown)
    {
        SwingUtilities.invokeLater(() -> {
            bountyPanel.removeAll();

            if (nextCountdown != null)
            {
                JLabel countdown = new JLabel(nextCountdown);
                countdown.setFont(FONT_BOLD);
                countdown.setForeground(new Color(241, 196, 15));
                countdown.setAlignmentX(Component.LEFT_ALIGNMENT);
                bountyPanel.add(countdown);
                bountyPanel.add(Box.createVerticalStrut(4));
            }

            if (bounties != null)
            {
                for (BingoBounty b : bounties)
                {
                    String status;
                    Color color;
                    if (!b.getWinner().isEmpty())
                    {
                        status = "Won by " + b.getWinner() + " (+" + NUM_FMT.format(b.getPoints()) + " pts)";
                        color = new Color(46, 204, 113);
                    }
                    else if (b.isReleaseFired())
                    {
                        status = "LIVE";
                        color = new Color(231, 76, 60);
                    }
                    else
                    {
                        status = "Upcoming";
                        color = new Color(150, 150, 150);
                    }

                    JLabel row = new JLabel("#" + b.getNumber() + " " + b.getDescription() + " — " + status);
                    row.setFont(FONT_SMALL);
                    row.setForeground(color);
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    bountyPanel.add(row);
                    bountyPanel.add(Box.createVerticalStrut(2));
                }
            }

            bountyPanel.revalidate();
            bountyPanel.repaint();
        });
    }

    public void updateDropLog(List<Map<String, Object>> drops)
    {
        SwingUtilities.invokeLater(() -> {
            dropLogPanel.removeAll();
            if (drops == null || drops.isEmpty())
            {
                JLabel none = new JLabel("No drops yet");
                none.setFont(FONT_ITALIC);
                none.setForeground(new Color(100, 100, 100));
                dropLogPanel.add(none);
            }
            else
            {
                int shown = Math.min(20, drops.size());
                for (int i = 0; i < shown; i++)
                {
                    Map<String, Object> d = drops.get(i);
                    String text = d.get("rsn") + " — " + d.get("item") + " [" + d.get("tile") + "] +" +
                        NUM_FMT.format(((Number) d.get("points")).doubleValue()) + " pts";
                    JLabel row = new JLabel(text);
                    row.setFont(FONT_SMALL);
                    row.setForeground(new Color(200, 200, 200));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    dropLogPanel.add(row);
                    dropLogPanel.add(Box.createVerticalStrut(2));
                }
            }
            dropLogPanel.revalidate();
            dropLogPanel.repaint();
        });
    }

    private void rebuildGrid()
    {
        gridPanel.removeAll();
        if (config == null) return;

        int rows = config.getGridRows();
        int cols = config.getGridCols();
        gridPanel.setLayout(new GridLayout(rows, cols, 1, 1));

        // Calculate cell size to fit panel width (~220px for RuneLite side panel)
        int cellSize = Math.max(20, Math.min(40, 210 / Math.max(cols, 1)));
        gridPanel.setMaximumSize(new Dimension(cols * (cellSize + 1), rows * (cellSize + 1)));
        gridPanel.setPreferredSize(new Dimension(cols * (cellSize + 1), rows * (cellSize + 1)));

        // Build grid sorted by row, col
        BingoTile[][] grid = new BingoTile[rows][cols];
        for (BingoTile tile : config.getTiles())
        {
            if (tile.getRow() < rows && tile.getCol() < cols)
            {
                grid[tile.getRow()][tile.getCol()] = tile;
            }
        }

        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                BingoTile tile = grid[r][c];
                JLabel cell = new JLabel(tile != null ? tile.getCode() : "", SwingConstants.CENTER);
                cell.setFont(new Font("Segoe UI", Font.BOLD, cellSize > 30 ? 9 : 7));
                cell.setForeground(Color.WHITE);
                cell.setOpaque(true);
                cell.setBackground(TILE_GRAY);
                cell.setPreferredSize(new Dimension(cellSize, cellSize));
                cell.setBorder(BorderFactory.createLineBorder(new Color(30, 30, 30), 1));

                if (tile != null)
                {
                    cell.setToolTipText(tile.getName());
                    final BingoTile t = tile;
                    cell.addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            showTileDetail(t);
                        }
                    });
                }

                gridPanel.add(cell);
            }
        }

        refreshGridColors();
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void refreshGridColors()
    {
        if (config == null || teamProgress == null) return;

        int cols = config.getGridCols();
        Component[] cells = gridPanel.getComponents();

        for (BingoTile tile : config.getTiles())
        {
            int idx = tile.getRow() * cols + tile.getCol();
            if (idx < 0 || idx >= cells.length) continue;

            double points = teamProgress.getOrDefault(tile.getCode(), 0.0);
            double pct = tile.getMax() > 0 ? (points / tile.getMax()) * 100 : 0;

            Color bg;
            if (pct >= 100) bg = TILE_BLACK;
            else if (pct >= 50) bg = TILE_GREEN;
            else if (pct >= 5) bg = TILE_YELLOW;
            else bg = TILE_GRAY;

            cells[idx].setBackground(bg);
        }
    }

    private void showTileDetail(BingoTile tile)
    {
        selectedTileCode = tile.getCode();
        tileDetailPanel.removeAll();

        JLabel name = new JLabel(tile.getCode() + ": " + tile.getName());
        name.setFont(FONT_BOLD);
        name.setForeground(ACCENT_GOLD);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.add(name);

        String typeLabel = "drop".equals(tile.getType()) ? "Drops" :
            "kc".equals(tile.getType()) ? "Kill Count" : "XP";
        JLabel type = new JLabel("Type: " + typeLabel +
            (tile.getMetric().isEmpty() ? "" : " (" + tile.getMetric() + ")"));
        type.setFont(FONT_SMALL);
        type.setForeground(new Color(170, 170, 170));
        type.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.add(type);

        double points = teamProgress != null ? teamProgress.getOrDefault(tile.getCode(), 0.0) : 0;
        double pct = tile.getMax() > 0 ? (points / tile.getMax()) * 100 : 0;
        JLabel progress = new JLabel("Progress: " + NUM_FMT.format(points) + " / " +
            NUM_FMT.format(tile.getMax()) + " (" + (int) pct + "%)");
        progress.setFont(FONT_SMALL);
        progress.setForeground(new Color(200, 200, 200));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileDetailPanel.add(progress);

        tileDetailPanel.setVisible(true);
        tileDetailPanel.revalidate();
        tileDetailPanel.repaint();
    }

    private JLabel createSectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(ACCENT_GOLD);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
