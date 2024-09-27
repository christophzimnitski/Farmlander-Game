package entities;

import levels.LevelManager;
import utilz.LoadSave;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;

import static utilz.Constants.InventoryConstants.Watermelon;
import static utilz.Constants.PlayerActionConstants.*;
import static utilz.Constants.Zoom.zoomMultiplier;

public class Player extends Entity {
    //BUGS: Tiles connect more than needed with one till --> add extra check for edge blocks
    //      Overlays for player and collidables create extra shadow --> make logic if statements in the main draw function for y positions

    public Inventory inventory = new Inventory(10);
    float playerWidth = (int) ((32 * 5) * zoomMultiplier), playerHeight = (int) ((32 * 5) * zoomMultiplier);
    int animationSelectionNumber = 0;
    float playerSpeed = 1.25f * zoomMultiplier;
    int currentIndex;
    int toolAnimationIndex = 0;
    String direction = "Right";
    Rectangle2D hitbox = new Rectangle2D() {
        @Override
        public void setRect(double x, double y, double w, double h) {

        }

        @Override
        public int outcode(double x, double y) {
            return 0;
        }

        @Override
        public Rectangle2D createIntersection(Rectangle2D r) {
            Rectangle2D intersection = new Rectangle2D.Double();
            Rectangle2D.intersect(this, r, intersection);

            // If the intersection is not empty, return it; otherwise, return null
            if (!intersection.isEmpty()) {
                return intersection;
            } else {
                return null;
            }
        }

        @Override
        public Rectangle2D createUnion(Rectangle2D r) {
            return null;
        }

        @Override
        public double getX() {
            return (x + (50 * zoomMultiplier));
        }

        @Override
        public double getY() {
            return (y + (130 * zoomMultiplier));
        }

        @Override
        public double getWidth() {
            return (60 * zoomMultiplier);
        }

        @Override
        public double getHeight() {
            return (32 * zoomMultiplier);
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    };
    Object[] loadedObjects = LevelManager.loadObjects("objects.dat").toArray();
    Collisions collisionsForLayer;
    private LevelManager levelManager;
    private BufferedImage[][] animations;
    private BufferedImage[][] wideAnimations;
    private int animationTick, animationIndex;
    private int playerAction = WALK_DOWN;
    private boolean moving = false, weapon = false;
    private boolean up, left, down, right;    int[] selectedAnimationArray = setAnimationArrays(animationSelectionNumber);
    public Player(float x, float y) {
        super(x, y);
        loadAnimations();
        try {
            setInventory((Inventory) loadedObjects[5]);
            setX((Float) loadedObjects[6]);
            setY((Float) loadedObjects[7]);
        } catch (Exception ignored) {

        }

        inventory.addInventoryItem(1, 3, Watermelon);
        System.out.println(Arrays.toString(inventory.getInventory()));
        System.out.println(Arrays.toString(inventory.getInventoryAmounts()));
        inventory.removeInventoryItem(1, 2);
        System.out.println(Arrays.toString(inventory.getInventory()));
        System.out.println(Arrays.toString(inventory.getInventoryAmounts()));
        inventory.removeInventoryItem(1, 2);
        System.out.println(Arrays.toString(inventory.getInventory()));
        System.out.println(Arrays.toString(inventory.getInventoryAmounts()));
    }

    public void addInventoryItem(int spot, int amount, int item) {
        inventory.addInventoryItem(spot, amount, item);
    }

    public void removeInventoryItem(int spot, int amount) {
        inventory.removeInventoryItem(spot, amount);
    }

    public void checkCollision(int[][] layer, int[] nonCollidables, double offset, double x, double y, double width, double height) {
        if (levelManager != null) {

            collisionsForLayer = new Collisions(layer, nonCollidables, offset * zoomMultiplier, width, height, x, y);

            for (int i = 0; i < collisionsForLayer.collideObjects.size(); i++) {

                if (collisionsForLayer.collideObjects.get(i).intersects(hitbox)) {

                    Rectangle2D intersection = hitbox.createIntersection(collisionsForLayer.collideObjects.get(i));

                    if (intersection.getWidth() > intersection.getHeight() + (0.3 * zoomMultiplier)) {
                        if (intersection.getY() > hitbox.getY()) {
                            this.y -= (float) (intersection.getHeight());
                        } else {
                            this.y += (float) (intersection.getHeight());
                        }
                    }
                    if (intersection.getHeight() > intersection.getWidth() + (0.3 * zoomMultiplier)) {
                        if (intersection.getX() > hitbox.getX()) {
                            this.x -= (float) (intersection.getWidth());
                        } else {
                            this.x += (float) (intersection.getWidth());
                        }
                    }
                }
            }
        }
    }

    /*public void checkCollisionAgain(int[][] layer, int[] nonCollidables, double offset, double x, double y, double width, double height) {
        if (levelManager != null) {

            collisionsForLayer = new Collisions(layer, nonCollidables, offset * zoomMultiplier, width, height, x, y);

            for (int i = 0; i < collisionsForLayer.collideObjects.size(); i++) {

                if (collisionsForLayer.collideObjects.get(i).intersects(hitbox)) {

                    System.out.println(hitbox.createIntersection(collisionsForLayer.collideObjects.get(i)));

                    Rectangle2D intersection = collisionsForLayer.collideObjects.get(i);

                    double overlapLeft = hitbox.getMaxX() - intersection.getX();
                    double overlapRight = intersection.getMaxX() - hitbox.getX();
                    double overlapTop = hitbox.getMaxY() - intersection.getY();
                    double overlapBottom = intersection.getMaxY() - hitbox.getY();

                    double hitboxOffset = hitbox.getWidth()- getHitbox().getHeight()+0.001;
                    double maxOverlap = height + hitbox.getHeight() - (0.5*zoomMultiplier);
                    //System.out.println("Left: " + overlapLeft + " Right: " + overlapRight + " Top: " + overlapTop + " Bottom " + overlapBottom);

                    if (overlapTop > overlapBottom && (overlapTop - overlapBottom) >= (overlapRight - overlapLeft - hitboxOffset) && (overlapTop - overlapBottom) >= (overlapLeft - overlapRight - hitboxOffset)) {
                        this.y = (float) ((intersection.getMaxY() - (130 * zoomMultiplier)) + (0.75*zoomMultiplier));
                    }
                    if (overlapBottom > overlapTop && (overlapBottom - overlapTop) >= (overlapRight - overlapLeft - hitboxOffset) && (overlapBottom - overlapTop) >= (overlapLeft - overlapRight - hitboxOffset)) {
                        this.y = (float) ((intersection.getY() - hitbox.getHeight() - (130 * zoomMultiplier)) - (0.75*zoomMultiplier));
                    }
                    if (overlapRight > overlapLeft && (overlapRight - overlapLeft) > (overlapBottom - overlapTop + hitboxOffset) && (overlapRight - overlapLeft) > (overlapTop - overlapBottom + hitboxOffset) && overlapBottom <= maxOverlap && overlapTop <= maxOverlap) {
                        this.x = (float) ((intersection.getX() - hitbox.getWidth() - (50 * zoomMultiplier)) - (1*zoomMultiplier));
                    }
                    if (overlapLeft > overlapRight && (overlapLeft - overlapRight) > (overlapBottom - overlapTop + hitboxOffset) && (overlapLeft - overlapRight) > (overlapTop - overlapBottom + hitboxOffset) && overlapBottom <= maxOverlap && overlapTop <= maxOverlap) {
                        this.x = (float) ((intersection.getMaxX() - (50 * zoomMultiplier)) + (1*zoomMultiplier));
                    }
                }
            }
        }
    }*/

    public void update() {
        moving = isMoving();
        updateAnimationTick();
        setAnimation();
        checkAllCollisions();
    }

    public void render(Graphics g, float offsetX, float offsetY) {
        g.drawImage(animations[playerAction][animationIndex], (int) x - (int) offsetX, (int) y - (int) offsetY, (int) ((32 * 5) * zoomMultiplier), (int) ((32 * 5) * zoomMultiplier), null);

        /*for (int x = 0; x < levelManager.getCollisionLayer().getLayerArray().length; x++)
            for (int y = 0; y < levelManager.getCollisionLayer().getLayerArray()[x].length; y++) {
                g.drawRect((int)(zoomMultiplier*(x*64 + 48)), (int)(zoomMultiplier*(y*64 + 120)), (int)(zoomMultiplier*32), (int)(zoomMultiplier*24));
            }**/
        /*for (int x = 0; x < levelManager.getCollisionLayer().getLayerArray().length; x++)
            for (int y = 0; y < levelManager.getCollisionLayer().getLayerArray()[x].length; y++) {
                g.drawRect((int)(zoomMultiplier*(x*64)), (int)(zoomMultiplier*(y*64)), (int)(zoomMultiplier*64), (int)(zoomMultiplier*64));
            }**/
        //g.drawRect((int) hitbox.getX(), (int) hitbox.getY(), (int) hitbox.getWidth(), (int) hitbox.getHeight());
    }

    private void checkAllCollisions() {
        if (levelManager != null) {
            checkCollision(levelManager.getCollisionLayer().getLayerArray(), new int[]{7, 8, 9, 25, 26, 27, 43, 44, 45}, 0, 0, 0, 64 * zoomMultiplier, 64 * zoomMultiplier);
            checkCollision(levelManager.getCropsLayer().getLayerArray(), new int[]{0, 5, 6, 10, 11, 15, 16, 20, 25, 26, 30, 31, 35, 36, 40, 41, 45, 50, 51, 55, 60, 61, 65, 66, 70, 71, 75, 76, 80, 81, 85, 170, 175, 201, 205, 210, 215, 220, 231, 236, 237, 240, 245, 246, 251, 256, 261, 291, 296, 301}, 0, (32 + 16), (80 + 40), 32 * zoomMultiplier, 24 * zoomMultiplier);
        }
    }

    private void setAnimation() {

        int startingAction = playerAction;
        if (moving && !weapon) {
            if (up) {
                playerAction = selectedAnimationArray[0];
                direction = "Up";
            }
            if (down) {
                playerAction = selectedAnimationArray[2];
                direction = "Down";
            }
            if (left) {
                playerAction = selectedAnimationArray[1];
                direction = "Left";
            }
            if (right) {
                playerAction = selectedAnimationArray[3];
                direction = "Right";
            }
        }

        boolean startingActionChanged = startingAction != playerAction;

        if (startingActionChanged) {
            resetAniTick();
        }

    }

    public int[] setAnimationArrays(int change) {
        animationSelectionNumber += change;
        int[][] animationArrays = {{WALK_UP, WALK_LEFT, WALK_DOWN, WALK_RIGHT},
                {WALK_CHOPPING_UP, WALK_CHOPPING_LEFT, WALK_CHOPPING_DOWN, WALK_CHOPPING_RIGHT},
                {WALK_MINING_UP, WALK_MINING_LEFT, WALK_MINING_DOWN, WALK_MINING_RIGHT},
                {WALK_WATERING_UP, WALK_WATERING_LEFT, WALK_WATERING_DOWN, WALK_WATERING_RIGHT},
                {WALK_TILLING_UP, WALK_TILLING_LEFT, WALK_TILLING_DOWN, WALK_TILLING_RIGHT},
                {WALK_DIGGING_UP, WALK_DIGGING_LEFT, WALK_DIGGING_DOWN, WALK_DIGGING_RIGHT}};

        if (animationSelectionNumber >= animationArrays.length) {
            animationSelectionNumber = 0;
        }

        this.selectedAnimationArray = animationArrays[animationSelectionNumber];

        if (Objects.equals(direction, "Up")) {
            playerAction = selectedAnimationArray[0];
            direction = "Up";
        }
        if (Objects.equals(direction, "Down")) {
            playerAction = selectedAnimationArray[2];
            direction = "Down";
        }
        if (Objects.equals(direction, "Left")) {
            playerAction = selectedAnimationArray[1];
            direction = "Left";
        }
        if (Objects.equals(direction, "Right")) {
            playerAction = selectedAnimationArray[3];
            direction = "Right";
        }

        return animationArrays[animationSelectionNumber];
    }

    public int getAttackAnimation(int incomingAction) {
        switch (incomingAction) {
            case WALK_UP:
            case WALK_LEFT:
            case WALK_DOWN:
            case WALK_RIGHT:
            case WALK_CHOPPING_UP:
            case WALK_CHOPPING_LEFT:
            case WALK_CHOPPING_DOWN:
            case WALK_CHOPPING_RIGHT:
                weapon = false;
                return incomingAction;

            case WATERING_UP:
            case WATERING_LEFT:
            case WATERING_DOWN:
            case WATERING_RIGHT:
            case TILLING_UP:
            case TILLING_LEFT:
            case TILLING_DOWN:
            case TILLING_RIGHT:
            case DIGGING_UP:
            case DIGGING_LEFT:
            case DIGGING_DOWN:
            case DIGGING_RIGHT:
            case MINE_UP:
            case MINE_LEFT:
            case MINE_DOWN:
            case MINE_RIGHT:
                weapon = true;
                return incomingAction;

            case WALK_WATERING_UP:
                weapon = true;
                return WATERING_UP;
            case WALK_WATERING_LEFT:
                weapon = true;
                return WATERING_LEFT;
            case WALK_WATERING_DOWN:
                weapon = true;
                return WATERING_DOWN;
            case WALK_WATERING_RIGHT:
                weapon = true;
                return WATERING_RIGHT;
            case WALK_TILLING_UP:
                weapon = true;
                return TILLING_UP;
            case WALK_TILLING_LEFT:
                weapon = true;
                return TILLING_LEFT;
            case WALK_TILLING_DOWN:
                weapon = true;
                return TILLING_DOWN;
            case WALK_TILLING_RIGHT:
                weapon = true;
                return TILLING_RIGHT;
            case WALK_DIGGING_UP:
                weapon = true;
                return DIGGING_UP;
            case WALK_DIGGING_LEFT:
                weapon = true;
                return DIGGING_LEFT;
            case WALK_DIGGING_DOWN:
                weapon = true;
                return DIGGING_DOWN;
            case WALK_DIGGING_RIGHT:
                weapon = true;
                return DIGGING_RIGHT;
            case WALK_MINING_UP:
                weapon = true;
                return MINE_UP;
            case WALK_MINING_LEFT:
                weapon = true;
                return MINE_LEFT;
            case WALK_MINING_DOWN:
                weapon = true;
                return MINE_DOWN;
            case WALK_MINING_RIGHT:
                weapon = true;
                return MINE_RIGHT;

            default:
                throw new IllegalStateException("Unexpected value: " + incomingAction);
        }
    }


    private void resetAniTick() {
        animationTick = 0;
        animationIndex = 0;
    }

    private void updateAnimationTick() {
        animationTick++;
        int aniSpeed = 16;
        int firstFrameOfAnimation = 0;
        int secondFrameOfAnimation = 1;

        if (animationTick >= aniSpeed && !weapon) {

            animationTick = 0;
            animationIndex++;
            if (!moving) {
                animationIndex = firstFrameOfAnimation;
            } else if (animationIndex >= GetSpritesInAction(playerAction)) {
                if (GetSpritesInAction(playerAction) == 9)
                    animationIndex = secondFrameOfAnimation;
                if (GetSpritesInAction(playerAction) == 8)
                    animationIndex = firstFrameOfAnimation;
                if (GetSpritesInAction(playerAction) == 4)
                    animationIndex = firstFrameOfAnimation;
            }
            currentIndex = animationIndex;

        }
        if (animationTick >= aniSpeed) {
            int startAni = playerAction;

            playerAction = getAttackAnimation(playerAction);

            animationTick = 0;
            if (GetSpritesInAction(playerAction) != 9) {
                animationIndex = toolAnimationIndex;

                useTool(animationSelectionNumber, toolAnimationIndex);

                toolAnimationIndex++;
            }
            if (GetSpritesInAction(playerAction) == 9) animationIndex = currentIndex;
            if (toolAnimationIndex >= 8) {
                playerAction = startAni;
                weapon = false;
                toolAnimationIndex = firstFrameOfAnimation;
                animationIndex = firstFrameOfAnimation;
            }
        }
    }

    public void useTool(int toolChoice, int toolAnimationIndex) {
        if (getUseToolValues("Hoe", toolChoice, toolAnimationIndex)) {
            if (Objects.equals(direction, "Right"))
                levelManager.getPlantingLayer().editLayer((int) x + (int) (122 * zoomMultiplier), (int) y + (int) (104 * zoomMultiplier), 400);
            if (Objects.equals(direction, "Down"))
                levelManager.getPlantingLayer().editLayer((int) x + (int) (47 * zoomMultiplier), (int) y + (int) ((32 * 4) * zoomMultiplier), 400);
            if (Objects.equals(direction, "Up"))
                levelManager.getPlantingLayer().editLayer((int) x + (int) (47 * zoomMultiplier), (int) y + (int) (32 * zoomMultiplier), 400);
            if (Objects.equals(direction, "Left"))
                levelManager.getPlantingLayer().editLayer((int) x - (int) (28 * zoomMultiplier), (int) y + (int) (104 * zoomMultiplier), 400);
        }
    }

    public boolean getUseToolValues(String tool, int toolChoice, int toolAnimationIndex) {
        if (tool.equals("Hoe")) {
            return (toolChoice == 4 && toolAnimationIndex == 5);
        } else return false;
    }

    private boolean isMoving() {

        if (weapon) return false;

        boolean moving = false;


        if (left && !right && !down && !up) {
            x -= playerSpeed;
            moving = true;
        } else if (right && !left && !down && !up) {
            x += playerSpeed;
            moving = true;
        }

        if (up && !down && !left && !right) {
            y -= playerSpeed;
            moving = true;
        } else if (down && !up && !left && !right) {
            y += playerSpeed;
            moving = true;
        }

        if (up && right) {
            x += (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            y -= (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            moving = true;
        } else if (down && right) {
            x += (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            y += (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            moving = true;
        } else if (up && left) {
            x -= (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            y -= (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            moving = true;
        } else if (down && left) {
            x -= (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            y += (float) (Math.sqrt((playerSpeed * playerSpeed) + (playerSpeed * playerSpeed))) / 2;
            moving = true;
        }
        return moving;

    }

    private void loadAnimations() {
        BufferedImage img = LoadSave.GetSpriteAtlas(LoadSave.PLAYER_ATLAS);

        animations = new BufferedImage[36][9];
        wideAnimations = new BufferedImage[18][5];

        for (int j = 0; j < animations.length; j++)
            for (int i = 0; i < animations[j].length; i++)
                animations[j][i] = img.getSubimage(i * 64, j * 64, 64, 64);
        for (int j = 0; j < wideAnimations.length; j++)
            for (int i = 0; i < wideAnimations[j].length; i++)
                wideAnimations[j][i] = img.getSubimage(i * 128, j * 128, 128, 128);

    }

    public void resetDirBooleans() {
        up = false;
        left = false;
        down = false;
        right = false;
    }

    public void setAttacking(boolean attacking) {
        this.weapon = attacking;
    }

    public void setUp(boolean up) {
        this.up = up;
    }

    public void setLeft(boolean left) {
        this.left = left;
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    public void setRight(boolean right) {
        this.right = right;
    }

    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    public Rectangle2D getHitbox() {
        return hitbox;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public float getPlayerWidth() {
        return playerWidth;
    }
    public float getPlayerHeight() {
        return playerHeight;
    }
}
