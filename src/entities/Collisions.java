package entities;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static utilz.Constants.Zoom.zoomMultiplier;

public class Collisions {
    List<Rectangle2D> collideObjects = new ArrayList<>();

    public Collisions(int[][] layer, int[] nonCollideList, double width, double height, int xOffset, int yOffset) {
        for (int x = 0; x < layer.length; x++)
            for (int y = 0; y < layer[x].length; y++) {
                int finalX = x;
                int finalY = y;
                if (layer[x][y] >= 1 && Arrays.stream(nonCollideList).noneMatch(q -> q == layer[finalX][finalY])) {

                    double finalX1 = x;
                    double finalY1 = y;
                    collideObjects.add(new Rectangle2D() {
                        @Override
                        public double getX() {
                            return (finalX1 * 64 + xOffset) * zoomMultiplier;
                        }

                        @Override
                        public double getY() {
                            return (finalY1 * 64 + yOffset) * zoomMultiplier;
                        }

                        @Override
                        public double getWidth() {
                            return width;
                        }

                        @Override
                        public double getHeight() {
                            return height;
                        }

                        @Override
                        public boolean isEmpty() {
                            return false;
                        }

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
                    });
                }
            }
    }

    public void addCollidableObject(int width, int height, int xOffset, int yOffset, int x, int y, int[][] layer, int[] nonCollideList) {
        collideObjects.add(new Rectangle2D() {
            @Override
            public double getX() {
                return (x * 64 + xOffset) * zoomMultiplier;
            }

            @Override
            public double getY() {
                return (y * 64 + yOffset) * zoomMultiplier;
            }

            @Override
            public double getWidth() {
                return width;
            }

            @Override
            public double getHeight() {
                return height;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

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
        });
    }
}
