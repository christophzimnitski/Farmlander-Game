package entities;

public class Camera {
    float width, height; // Screen size
    Player player;

    public Camera(float width, float height, Player player) {
        this.width = width;
        this.height = height;
        this.player = player;
    }

    public int getOffsetX() {
        // Center the camera based in the middle of the player
        if (player.x - width / 2 + player.getPlayerWidth() / 2 <= 0) return 0;
        else return (int) (player.x + player.getPlayerWidth() / 2 - width / 2);
    }

    public int getOffsetY() {
        // Center the camera based in the middle of the player
        if (player.y - height / 2 + player.getPlayerHeight() / 2 <= 0) return 0;
        else return (int) (player.y + player.getPlayerHeight() / 2 - height / 2);
    }
}
