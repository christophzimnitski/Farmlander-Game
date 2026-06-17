package tools.data;

/**
 * The built-in tool Q-action catalog: the five actions that used to be hardcoded in PlayState. Serves
 * two roles — the fallback when {@code data/tool_actions.json} is missing/unparseable, and the source
 * of truth the JSON file was generated from (keep the two in sync).
 *
 * <p>Direction rows are always [up, left, down, right]. The mining sheet (player_mining.png) holds the
 * 64px clothed-action rows (tilling 0-3, digging 4-7, watering 20-23) and the oversized 128px pickaxe
 * swing (rows 46/48/50/52). The chopping sheet holds the matching oversized axe swing. The oversized
 * swings are sliced 6-wide but play [5,4,3,1,0] — the sheet runs reversed and frame 2 has no tool.
 */
public final class DefaultToolActionDataSource implements ToolActionDataSource {
    @Override
    public ToolActionBlueprint[] loadToolActionBlueprints() {
        int[] swingRows = {46, 48, 50, 52};
        int[] swingOrder = {5, 4, 3, 1, 0};
        return new ToolActionBlueprint[] {
            // Tilling: the only action with a world effect — tills the faced 2x2 plot at frame 5.
            new ToolActionBlueprint(4, "Tilling", "mining", new int[]{0, 1, 2, 3}, 8, null, 5, "till", false),
            // Watering: 8 frames (the old Swing getSpritesInAction said 4, which looked abrupt). No effect yet.
            new ToolActionBlueprint(3, "Watering", "mining", new int[]{20, 21, 22, 23}, 8, null, -1, null, false),
            // Digging: shovel, 8 frames. No effect yet.
            new ToolActionBlueprint(5, "Digging", "mining", new int[]{4, 5, 6, 7}, 8, null, -1, null, false),
            // Chopping: oversized axe swing from the chopping sheet.
            new ToolActionBlueprint(1, "Chopping", "chopping", swingRows, 6, swingOrder, -1, null, true),
            // Mining: oversized pickaxe swing from the mining sheet (same layout/order as chopping).
            new ToolActionBlueprint(2, "Mining", "mining", swingRows, 6, swingOrder, -1, null, true),
        };
    }
}
