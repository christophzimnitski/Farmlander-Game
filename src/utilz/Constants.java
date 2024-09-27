package utilz;

public class Constants {
    public static class Zoom {
        public static float zoomMultiplier = 1.5f;
    }

    public static class InventoryConstants {
        public static final int Nothing = 0;
        public static final int Axe = 1;
        public static final int Pickaxe = 2;
        public static final int WateringCan = 3;
        public static final int Hoe = 4;
        public static final int Shovel = 5;
        public static final int Potato = 100;
        public static final int GoldPotato = 101;
        public static final int PurplePotato = 102;
        public static final int SweetPotato = 103;
        public static final int Cassava = 104;
        public static final int DaikonRadish = 105;
        public static final int Carrot = 106;
        public static final int Parsnip = 107;
        public static final int Radish = 108;
        public static final int Beet = 109;
        public static final int Turnip = 110;
        public static final int Rutabaga = 111;
        public static final int Garlic = 112;
        public static final int YellowOnion = 113;
        public static final int RedOnion = 114;
        public static final int WhiteOnion = 115;
        public static final int Scallion = 116;
        public static final int HotPepper = 117;
        public static final int GreenBellPepper = 118;
        public static final int RedBellPepper = 119;
        public static final int OrangeBellPepper = 120;
        public static final int YellowBellPepper = 121;
        public static final int ChiliPepper = 123;
        public static final int Watermelon = 124;
        public static final int Honeydew = 125;
        public static final int Cantaloupe = 126;
        public static final int AcornSquash = 127;
        public static final int Pumpkin = 128;
        public static final int CrookneckSquash = 129;
        public static final int ButternutSquash = 130;
        public static final int Corn = 131;
        public static final int Maize = 132;
        public static final int Asparagus = 133;
        public static final int Rhubarb = 134;
        public static final int RomaineLettuce = 135;
        public static final int IcebergLettuce = 136;
        public static final int Kale = 137;
        public static final int RedCabbage = 138;
        public static final int Cabbage = 139;
        public static final int Celery = 140;
        public static final int BokChoy = 141;
        public static final int FennelBulb = 142;
        public static final int BrusselsSprouts = 143;
        public static final int Cauliflower = 144;
        public static final int Broccoli = 145;
        public static final int Artichoke = 146;
        public static final int Leek = 147;
        public static final int Kohlrabi = 148;
        public static final int Eggplant = 149;
        public static final int Zucchini = 150;
        public static final int YellowSquash = 151;
        public static final int Cucumber = 152;
        public static final int Strawberry = 153;
        public static final int Blackberry = 154;
        public static final int Raspberry = 156;
        public static final int Blueberry = 157;
        public static final int Grapes = 158;
        public static final int GreenGrapes = 159;
        public static final int CherryTomatoes = 160;
        public static final int Tomato = 161;
        public static final int LargeTomato = 162;
        public static final int Peas = 163;
        public static final int Hops = 164;
        public static final int GreenBeans = 165;
        public static final int Coffee = 166;
        public static final int Pineapple = 167;
        public static final int Kiwi = 168;
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

        public static int GetSpritesInAction(int player_action) {
            switch (player_action) {

                case WATERING_UP:
                case WATERING_LEFT:
                case WATERING_DOWN:
                case WATERING_RIGHT:
                    return 4;
                case TILLING_UP:
                case TILLING_LEFT:
                case TILLING_DOWN:
                case TILLING_RIGHT:
                case DIGGING_UP:
                case DIGGING_LEFT:
                case DIGGING_DOWN:
                case DIGGING_RIGHT:
                    return 8;
                case WALK_MINING_UP:
                case WALK_MINING_LEFT:
                case WALK_MINING_DOWN:
                case WALK_MINING_RIGHT:
                case WALK_TILLING_UP:
                case WALK_TILLING_LEFT:
                case WALK_TILLING_DOWN:
                case WALK_TILLING_RIGHT:
                case WALK_DIGGING_UP:
                case WALK_DIGGING_LEFT:
                case WALK_DIGGING_DOWN:
                case WALK_DIGGING_RIGHT:
                case WALK_WATERING_UP:
                case WALK_WATERING_LEFT:
                case WALK_WATERING_DOWN:
                case WALK_WATERING_RIGHT:
                case WALK_CHOPPING_UP:
                case WALK_CHOPPING_LEFT:
                case WALK_CHOPPING_DOWN:
                case WALK_CHOPPING_RIGHT:
                case WALK_UP:
                case WALK_LEFT:
                case WALK_DOWN:
                case WALK_RIGHT:
                    return 9;
                default:
                    return 2;


            }
        }
    }

}
