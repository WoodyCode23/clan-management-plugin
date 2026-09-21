package com.droplogger;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The server sends a tile's row/col ONE-based (a board is authored in a spreadsheet whose top-left
 * tile is row 1, col 1, and the server's tileWithinGrid rejects anything below 1). The panel used
 * to treat them as zero-based, which left the top-left cell empty and silently dropped the entire
 * last row and column off a board. The dev preview hid it by using zero-based sample data.
 */
public class BingoBoardGridTest
{
    private static BingoTile at(String code, int row, int col)
    {
        return BingoTile.item(code, code, row, col, 0, 1, 0, BingoTile.KIND_DROP, false);
    }

    @Test
    public void everyCornerOfAOneBasedBoardIsPlaced()
    {
        List<BingoTile> tiles = Arrays.asList(
            at("A1", 1, 1), at("E1", 1, 5), at("A5", 5, 1), at("E5", 5, 5), at("C3", 3, 3));

        BingoTile[][] grid = BingoBoardPanel.layOutForTest(tiles, 5, 5);

        assertNotNull("top-left tile must be placed", grid[0][0]);
        assertEquals("A1", grid[0][0].code);
        assertNotNull("last column must not be dropped", grid[0][4]);
        assertEquals("E1", grid[0][4].code);
        assertNotNull("last row must not be dropped", grid[4][0]);
        assertEquals("A5", grid[4][0].code);
        assertNotNull("bottom-right tile must be placed", grid[4][4]);
        assertEquals("E5", grid[4][4].code);
        assertEquals("C3", grid[2][2].code);
    }

    @Test
    public void aTileOutsideTheGridIsIgnoredRatherThanThrowing()
    {
        // A host who shrinks a board leaves tiles behind; the panel must not blow up on them.
        BingoTile[][] grid = BingoBoardPanel.layOutForTest(
            Arrays.asList(at("E5", 5, 5), at("Z9", 9, 9), at("bad", 0, 0)), 3, 3);
        for (BingoTile[] gridRow : grid)
        {
            for (BingoTile t : gridRow)
            {
                assertEquals(null, t);
            }
        }
    }
}
