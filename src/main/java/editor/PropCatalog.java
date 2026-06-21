package editor;

/**
 * A curated set of free-placeable props cut from {@code tilesets/farming_fishing.png} (Daneeklu's LPC farm
 * pack) — the market stall, produce crates, boats, a dock, a log pile and a grain sack. These are the
 * multi-tile pieces that don't fit the 32px tile grid, so the editor drops them as {@link world.PlacedObject}s
 * instead of painting cells.
 *
 * <p>Each prop is just a source rect (in sheet pixels) plus a label — plain data, so the list is easy to
 * extend by eye from the sheet. Rects were read off a 32px grid laid over the sheet; nudge them here if a
 * prop looks clipped. Append-only is not required (props aren't stored by index in the save — placed objects
 * store their own source rect), so this list can be reordered freely.
 */
public final class PropCatalog {
    /** The single sheet every prop is cut from. */
    public static final String SHEET = "tilesets/farming_fishing.png";

    /** One placeable prop: a named source rect in {@link #SHEET}. */
    public static final class Prop {
        public final String name;
        public final int x, y, w, h; // source rect in sheet pixels

        Prop(String name, int x, int y, int w, int h) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final Prop[] PROPS = {
            new Prop("Market stall",  160,  96,  96, 160),
            new Prop("Crate tomato",  288, 288,  32,  68),
            new Prop("Crate cabbage", 352, 288,  32,  68),
            new Prop("Crate potato",  416, 288,  32,  68),
            new Prop("Boat",          452, 448, 196,  96),
            new Prop("Boat in water", 452, 510, 196, 120),
            new Prop("Dock",          448,   0, 160,  96),
            new Prop("Log pile",       32,   0,  64,  64),
            new Prop("Grain sack",      0,  96,  64,  70),
    };

    private PropCatalog() {}

    public static int count() { return PROPS.length; }

    public static Prop get(int i) { return PROPS[i]; }
}
