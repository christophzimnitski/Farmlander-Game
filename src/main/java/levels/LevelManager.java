package levels;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Static load/save of the serialized world objects. The Swing-era instance side (which wired up the
 * individual layers and held a {@code main.Level}) was removed in the libGDX cutover — {@code PlayState}
 * reads the save directly via {@link #loadObjects}.
 */
public final class LevelManager {

    // Saves live in a per-user data dir, never inside the project/repo.
    public static final String SAVE_FILE =
            System.getProperty("user.home") + File.separator + ".farmlander" + File.separator + "objects.dat";

    private LevelManager() {
    }

    public static void saveObjects(List<Object> objects, String fileName) {
        File parent = new File(fileName).getParentFile();
        if (parent != null) parent.mkdirs();
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
}
