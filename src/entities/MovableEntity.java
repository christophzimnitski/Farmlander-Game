package entities;

import levels.LevelManager;

import java.awt.geom.Rectangle2D;
import java.util.Objects;

import static utilz.Constants.Zoom.zoomMultiplier;

public abstract class MovableEntity extends AnimatedEntity {
    protected LevelManager levelManager;
    protected Collisions collisionsForLayer;
    protected Rectangle2D hitbox;
    protected Integer entityAction;
    protected Boolean isMoving;
    protected int[][] animationArrays;
    protected int animationSelectionNumber;
    protected int[] selectedAnimationArray;
    protected String movingDirection;

    public MovableEntity(float x, float y) {
        super(x, y);

    }

    protected void checkAllCollisions() {
        checkCollision(levelManager.getCollisionLayer().getLayerArray());
        checkCollision(levelManager.getCropsLayer().getLayerArray());
    }

    protected void checkCollision(int[][] layer) {
        if (levelManager != null) {

            collisionsForLayer = new Collisions(layer, (int[]) this.getCollisionItem(layer, "nonCollidables"), (Float) this.getCollisionItem(layer, "width"), (Float) this.getCollisionItem(layer, "height"), (int) this.getCollisionItem(layer, "offsetX"), (int) this.getCollisionItem(layer, "offsetY"));

            for (int i = 0; i < collisionsForLayer.collideObjects.size(); i++) {

                if (collisionsForLayer.collideObjects.get(i).intersects(hitbox)) {


                    Rectangle2D intersection = hitbox.createIntersection(collisionsForLayer.collideObjects.get(i));


                    if (intersection.getWidth() > intersection.getHeight() + (0.3 * zoomMultiplier)) {
                        if (intersection.getY() > hitbox.getY()) {
                            y -= (float) ((float) (intersection.getHeight()) + (0.3 * zoomMultiplier));
                        } else {
                            y += (float) ((float) (intersection.getHeight()) + (0.3 * zoomMultiplier));
                        }
                    }
                    if (intersection.getHeight() > intersection.getWidth() + (0.3 * zoomMultiplier)) {
                        if (intersection.getX() > hitbox.getX()) {
                            x -= (float) ((float) (intersection.getWidth()) + (0.3 * zoomMultiplier));;
                        } else {
                            x += (float) ((float) (intersection.getWidth()) + (0.3 * zoomMultiplier));;
                        }
                    }
                }
            }
        }
    }

    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    public void changeAnimationArray(int changeIncrement) {
        animationSelectionNumber += changeIncrement;

        if (animationSelectionNumber >= animationArrays.length) {
            animationSelectionNumber = 0;
        }

        selectedAnimationArray = animationArrays[animationSelectionNumber];

        if (Objects.equals(movingDirection, "Up")) {
            entityAction = selectedAnimationArray[0];
            movingDirection = "Up";
        }
        if (Objects.equals(movingDirection, "Down")) {
            entityAction = selectedAnimationArray[2];
            movingDirection = "Down";
        }
        if (Objects.equals(movingDirection, "Left")) {
            entityAction = selectedAnimationArray[1];
            movingDirection = "Left";
        }
        if (Objects.equals(movingDirection, "Right")) {
            entityAction = selectedAnimationArray[3];
            movingDirection = "Right";
        }

    }

    public boolean getMovementConditionals() {
        return true;
    }

    protected abstract void loadAnimations();

    protected Object getCollisionItem(int[][] layer, String item) {
        if (layer == levelManager.getCollisionLayer().getLayerArray()) {
            return switch (item) {
                case "nonCollidables" -> new int[]{7, 8, 9, 25, 26, 27, 43, 44, 45};
                case "height" -> 64 * zoomMultiplier;
                case "width" -> 64 * zoomMultiplier;
                case "offsetX", "offsetY" -> 0;
                default -> null;
            };
        }
        if (layer == levelManager.getCropsLayer().getLayerArray()) {
            return switch (item) {
                case "nonCollidables" ->
                        new int[]{0, 5, 6, 10, 11, 15, 16, 20, 25, 26, 30, 31, 35, 36, 40, 41, 45, 50, 51, 55, 60, 61, 65, 66, 70, 71, 75, 76, 80, 81, 85, 170, 175, 201, 205, 210, 215, 220, 231, 236, 237, 240, 245, 246, 251, 256, 261, 291, 296, 301};
                case "height" -> 24 * zoomMultiplier;
                case "width" -> 32 * zoomMultiplier;
                case "offsetX" -> (32 + 16);
                case "offsetY" -> (80 + 40);
                default -> null;
            };
        } else return null;
    }
}
