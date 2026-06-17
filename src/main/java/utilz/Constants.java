package utilz;

import static utilz.Constants.PlayerActionConstants.*;
import static utilz.Constants.PlayerActionConstants.WALK_DIGGING_RIGHT;

public class Constants {
    public static class Zoom {
        public static float zoomMultiplier = 1.5f;
    }

    public static class InventoryConstants {
        /**
         * Empty-slot sentinel. The tool ids (1-5) and crop ids (100-168) that used to live here are now
         * data-driven — tools come from {@code items.json}/{@code DefaultItemDataSource}, crop item ids
         * from {@code CropType.itemId}, both resolved through {@code items.ItemRegistry}.
         */
        public static final int Nothing = 0;
    }

    public static class PlayerActionConstants {


        public static final int TILLING_UP = 0;
        public static final int TILLING_LEFT = 1;
        public static final int TILLING_DOWN = 2;
        public static final int TILLING_RIGHT = 3;
        public static final int DIGGING_UP = 4;
        public static final int DIGGING_LEFT = 5;
        public static final int DIGGING_DOWN = 6;
        public static final int DIGGING_RIGHT = 7;
        public static final int WALK_MINING_UP = 8;
        public static final int WALK_MINING_LEFT = 9;
        public static final int WALK_MINING_DOWN = 10;
        public static final int WALK_MINING_RIGHT = 11;
        public static final int WALK_TILLING_UP = 12;
        public static final int WALK_TILLING_LEFT = 13;
        public static final int WALK_TILLING_DOWN = 14;
        public static final int WALK_TILLING_RIGHT = 15;
        public static final int WALK_DIGGING_UP = 16;
        public static final int WALK_DIGGING_LEFT = 17;
        public static final int WALK_DIGGING_DOWN = 18;
        public static final int WALK_DIGGING_RIGHT = 19;
        public static final int WATERING_UP = 20;
        public static final int WATERING_LEFT = 21;
        public static final int WATERING_DOWN = 22;
        public static final int WATERING_RIGHT = 23;
        public static final int WALK_WATERING_UP = 24;
        public static final int WALK_WATERING_LEFT = 25;
        public static final int WALK_WATERING_DOWN = 26;
        public static final int WALK_WATERING_RIGHT = 27;
        public static final int WALK_CHOPPING_UP = 28;
        public static final int WALK_CHOPPING_LEFT = 29;
        public static final int WALK_CHOPPING_DOWN = 30;
        public static final int WALK_CHOPPING_RIGHT = 31;
        public static final int WALK_UP = 32;
        public static final int WALK_LEFT = 33;
        public static final int WALK_DOWN = 34;
        public static final int WALK_RIGHT = 35;
        public static final int MINE_UP = 36;
        public static final int MINE_LEFT = 37;
        public static final int MINE_DOWN = 38;
        public static final int MINE_RIGHT = 39;

        public static int getSpritesInAction(int playerAction) {
            return switch (playerAction) {
                case WATERING_UP, WATERING_LEFT, WATERING_DOWN, WATERING_RIGHT -> 4;
                case TILLING_UP, TILLING_LEFT, TILLING_DOWN, TILLING_RIGHT, DIGGING_UP, DIGGING_LEFT, DIGGING_DOWN,
                     DIGGING_RIGHT -> 8;
                case WALK_MINING_UP, WALK_MINING_LEFT, WALK_MINING_DOWN, WALK_MINING_RIGHT, WALK_TILLING_UP,
                     WALK_TILLING_LEFT, WALK_TILLING_DOWN, WALK_TILLING_RIGHT, WALK_DIGGING_UP, WALK_DIGGING_LEFT,
                     WALK_DIGGING_DOWN, WALK_DIGGING_RIGHT, WALK_WATERING_UP, WALK_WATERING_LEFT, WALK_WATERING_DOWN,
                     WALK_WATERING_RIGHT, WALK_CHOPPING_UP, WALK_CHOPPING_LEFT, WALK_CHOPPING_DOWN, WALK_CHOPPING_RIGHT,
                     WALK_UP, WALK_LEFT, WALK_DOWN, WALK_RIGHT -> 9;
                default -> 2;
            };
        }

        public static final int[][] playerAnimationArrays = new int[][]{{WALK_UP, WALK_LEFT, WALK_DOWN, WALK_RIGHT},
                {WALK_CHOPPING_UP, WALK_CHOPPING_LEFT, WALK_CHOPPING_DOWN, WALK_CHOPPING_RIGHT},
                {WALK_MINING_UP, WALK_MINING_LEFT, WALK_MINING_DOWN, WALK_MINING_RIGHT},
                {WALK_WATERING_UP, WALK_WATERING_LEFT, WALK_WATERING_DOWN, WALK_WATERING_RIGHT},
                {WALK_TILLING_UP, WALK_TILLING_LEFT, WALK_TILLING_DOWN, WALK_TILLING_RIGHT},
                {WALK_DIGGING_UP, WALK_DIGGING_LEFT, WALK_DIGGING_DOWN, WALK_DIGGING_RIGHT}};
    }

}
