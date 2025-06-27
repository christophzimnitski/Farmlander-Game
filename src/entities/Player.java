package entities;

import levels.LevelManager;
import org.jetbrains.annotations.NotNull;
import utilz.Constants;
import utilz.LoadSave;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;

import static utilz.Constants.PlayerActionConstants.*;
import static utilz.Constants.Zoom.zoomMultiplier;

public class Player extends MovableEntity {
    //BUGS: Tiles connect more than needed with one till --> add extra check for edge blocks
    //      Overlays for player and collidables create extra shadow --> make logic if statements in the main draw function for y positions

    public Inventory inventory = new Inventory(10);
    float playerWidth = (int) ((32 * 5) * zoomMultiplier), playerHeight = (int) ((32 * 5) * zoomMultiplier);
    float playerSpeed = 1.25f * zoomMultiplier;
    int currentIndex;
    int toolAnimationIndex = 0;

    Object[] loadedObjects = LevelManager.loadObjects("objects.dat").toArray();
    private BufferedImage[][] animations;
    private BufferedImage[][] wideAnimations;
    private boolean moving = false, weapon = false;
    private boolean up, left, down, right;


    public Player(float x, float y) {
        super(x, y);

        hitbox = initPlayerHitbox();
        entityAction = 0;
        animationArrays = playerAnimationArrays;
        selectedAnimationArray = animationArrays[0];


        loadAnimations();

        try {
            setInventory((Inventory) loadedObjects[5]);
            setX((Float) loadedObjects[6]);
            setY((Float) loadedObjects[7]);
            setSelectedAnimationArray((int[]) loadedObjects[8]);
            setEntityAction((Integer) loadedObjects[9]);
        } catch (Exception ignored) {}

        System.out.println(Arrays.toString(inventory.getInventory()));
        System.out.println(Arrays.toString(inventory.getInventoryAmounts()));
    }


    public void addInventoryItem(int spot, int amount, int item) {
        inventory.addInventoryItem(spot, amount, item);
    }

    public void removeInventoryItem(int spot, int amount) {
        inventory.removeInventoryItem(spot, amount);
    }

    public void update() {
        moving = shouldIMove();
        updateAnimationTick();
        setAnimation();
        checkAllCollisions();
    }

    public void render(Graphics g, float offsetX, float offsetY) {
        g.drawImage(animations[this.entityAction][animationIndex], (int) x - (int) offsetX, (int) y - (int) offsetY, (int) ((32 * 5) * zoomMultiplier), (int) ((32 * 5) * zoomMultiplier), null);

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

    private void setAnimation() {

        int startingAction = this.entityAction;

        if (moving) {
            if (up) {
                this.entityAction = selectedAnimationArray[0];
                this.movingDirection = "Up";
            }
            if (down) {
                this.entityAction = selectedAnimationArray[2];
                this.movingDirection = "Down";
            }
            if (left) {
                this.entityAction = selectedAnimationArray[1];
                this.movingDirection = "Left";
            }
            if (right) {
                this.entityAction = selectedAnimationArray[3];
                this.movingDirection = "Right";
            }
        }

        boolean startingActionChanged = startingAction != this.entityAction;

        if (startingActionChanged) {
            resetAniTick();
        }

    }

    public int getAttackAnimation(int incomingAction) {
        return switch (incomingAction) {
            case WALK_UP:
            case WALK_LEFT:
            case WALK_DOWN:
            case WALK_RIGHT:
            case WALK_CHOPPING_UP:
            case WALK_CHOPPING_LEFT:
            case WALK_CHOPPING_DOWN:
            case WALK_CHOPPING_RIGHT:
                weapon = false;
                yield incomingAction;

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
                yield incomingAction;

            case WALK_WATERING_UP:
                weapon = true;
                yield WATERING_UP;
            case WALK_WATERING_LEFT:
                weapon = true;
                yield WATERING_LEFT;
            case WALK_WATERING_DOWN:
                weapon = true;
                yield WATERING_DOWN;
            case WALK_WATERING_RIGHT:
                weapon = true;
                yield WATERING_RIGHT;
            case WALK_TILLING_UP:
                weapon = true;
                yield TILLING_UP;
            case WALK_TILLING_LEFT:
                weapon = true;
                yield TILLING_LEFT;
            case WALK_TILLING_DOWN:
                weapon = true;
                yield TILLING_DOWN;
            case WALK_TILLING_RIGHT:
                weapon = true;
                yield TILLING_RIGHT;
            case WALK_DIGGING_UP:
                weapon = true;
                yield DIGGING_UP;
            case WALK_DIGGING_LEFT:
                weapon = true;
                yield DIGGING_LEFT;
            case WALK_DIGGING_DOWN:
                weapon = true;
                yield DIGGING_DOWN;
            case WALK_DIGGING_RIGHT:
                weapon = true;
                yield DIGGING_RIGHT;
            case WALK_MINING_UP:
                weapon = true;
                yield MINE_UP;
            case WALK_MINING_LEFT:
                weapon = true;
                yield MINE_LEFT;
            case WALK_MINING_DOWN:
                weapon = true;
                yield MINE_DOWN;
            case WALK_MINING_RIGHT:
                weapon = true;
                yield MINE_RIGHT;
            default:
                throw new IllegalStateException("Unexpected value: " + incomingAction);
        };
    }

    private void updateAnimationTick() {
        animationTick++;
        int aniSpeed = 16;
        int firstFrameOfAnimation = 0;
        int secondFrameOfAnimation = 1;

        if (animationIndex == 0 && moving) animationIndex = 1;

        if (animationTick >= aniSpeed && !weapon) {
            animationTick = 0;
            animationIndex++;
            if (!moving) {
                animationIndex = firstFrameOfAnimation;
            } else if (animationIndex >= GetSpritesInAction(this.entityAction)) {
                if (GetSpritesInAction(this.entityAction) == 9)
                    animationIndex = secondFrameOfAnimation;
                if (GetSpritesInAction(this.entityAction) == 8)
                    animationIndex = firstFrameOfAnimation;
                if (GetSpritesInAction(this.entityAction) == 4)
                    animationIndex = firstFrameOfAnimation;
            }
            currentIndex = animationIndex;

        }
        if (animationTick >= aniSpeed) {
            int startAni = this.entityAction;

            this.entityAction = getAttackAnimation(this.entityAction);

            animationTick = 0;

            if (GetSpritesInAction(this.entityAction) != 9) {
                animationIndex = toolAnimationIndex;

                useTool(arraySelectionNumber, toolAnimationIndex);

                toolAnimationIndex++;
            }

            if (GetSpritesInAction(this.entityAction) == 9) animationIndex = currentIndex;

            if (toolAnimationIndex >= 8) {
                this.entityAction = startAni;
                weapon = false;
                toolAnimationIndex = firstFrameOfAnimation;
                animationIndex = firstFrameOfAnimation;
            }
        }
    }

    public void useTool(int toolChoice, int toolAnimationIndex) {
        if (getUseToolValues("Hoe", toolChoice, toolAnimationIndex)) {
            if (Objects.equals(this.movingDirection, "Right"))
                levelManager.getPlantingLayer().editLayer((int) x + (int) (122 * zoomMultiplier), (int) y + (int) (104 * zoomMultiplier), 400);
            if (Objects.equals(this.movingDirection, "Down"))
                levelManager.getPlantingLayer().editLayer((int) x + (int) (47 * zoomMultiplier), (int) y + (int) ((32 * 4) * zoomMultiplier), 400);
            if (Objects.equals(this.movingDirection, "Up"))
                levelManager.getPlantingLayer().editLayer((int) x + (int) (47 * zoomMultiplier), (int) y + (int) (32 * zoomMultiplier), 400);
            if (Objects.equals(this.movingDirection, "Left"))
                levelManager.getPlantingLayer().editLayer((int) x - (int) (28 * zoomMultiplier), (int) y + (int) (104 * zoomMultiplier), 400);
        }
    }

    public boolean getUseToolValues(String tool, int toolChoice, int toolAnimationIndex) {
        if (tool.equals("Hoe")) {
            return (toolChoice == 4 && toolAnimationIndex == 5);
        } else return false;
    }

    private boolean shouldIMove() {

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

    protected void loadAnimations() {
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

    private @NotNull Rectangle2D initPlayerHitbox() {
        return new Rectangle2D() {
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
                return (Player.super.x + (50 * zoomMultiplier));
            }

            @Override
            public double getY() {
                return (Player.super.y + (130 * zoomMultiplier));
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
    }

}
