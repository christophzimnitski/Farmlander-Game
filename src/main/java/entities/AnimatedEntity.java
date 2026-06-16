package entities;

public class AnimatedEntity extends Entity {
    protected int animationTick, animationIndex;

    public AnimatedEntity(float x, float y) {
        super(x, y);
    }

    protected void resetAniTick() {
        animationTick = 0;
        animationIndex = 0;
    }
}
