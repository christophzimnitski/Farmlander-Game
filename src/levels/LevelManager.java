package levels;

import main.Level;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LevelManager implements java.io.Serializable {

    private final Level level;
    Object[] loadedObjects = LevelManager.loadObjects("objects.dat").toArray();

    List<Object> objects = new ArrayList<>();
    GrassLayer grassLayer = new GrassLayer();
    CollisionLayer collisionLayer;
    PlantingLayer plantingLayer;
    CropsLayer cropsLayer;

    public LevelManager(Level level) {
        this.level = level;
        collisionLayer = new CollisionLayer(level);
        plantingLayer = new PlantingLayer(level);
        cropsLayer = new CropsLayer(level);
        try {
            collisionLayer.setLayerArray((int[][]) loadedObjects[0]);
            plantingLayer.setLayerArray((int[][]) loadedObjects[1]);
            grassLayer.setLayerArray((int[][]) loadedObjects[2]);
            cropsLayer.setLayerArray((int[][]) loadedObjects[3]);
            cropsLayer.setTimesLeft((int[][]) loadedObjects[4]);
        } catch (Exception e) {
            grassLayer.editLayer();
        }
    }

    public static void saveObjects(List<Object> objects, String fileName) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {
            for (Object obj : objects) {
                oos.writeObject(obj);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Object> loadObjects(String fileName) {
        List<Object> objects = new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            while (true) {
                try {
                    Object obj = ois.readObject();
                    objects.add(obj);
                } catch (IOException e) {
                    break; // End of file reached
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return objects;
    }

    public GrassLayer getGrassLayer() {
        return grassLayer;
    }

    public CollisionLayer getCollisionLayer() {
        return collisionLayer;
    }

    public PlantingLayer getPlantingLayer() {
        return plantingLayer;
    }

    public CropsLayer getCropsLayer() {
        return cropsLayer;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public Object[] getLoadedObjects() {
        return loadedObjects;
    }
}
