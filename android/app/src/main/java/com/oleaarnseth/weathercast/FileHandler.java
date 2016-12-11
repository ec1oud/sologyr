package com.oleaarnseth.weathercast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Denne klassen tar seg av all fil I/O, som benyttes til blant annet
 * lagring av værikoner til værvarsler:
 */
public class FileHandler {
    // Navn på cache-undermappe der ikoner lagres:
    public static final String DIR_FILE_CACHE_ICONS = "weathericons";

    public static final String FILE_EXTENSION = ".png";

    public static final int BITMAP_COMPRESS_QUALITY = 100;

    public File saveToFile(Bitmap bm, int iconNumber, File cacheDir) {
        if (!isExtStorageAvailable()) {
            return null;
        }

        File dir = new File(cacheDir
                + File.separator
                + DIR_FILE_CACHE_ICONS);
        boolean dirExists = dir.exists();

        // Hvis mappe ikke fins, opprettes den:
        if (!dirExists) {
            dirExists = dir.mkdirs();
        }

        if (dirExists) {
            File iconFile = new File(dir, iconNumber + FILE_EXTENSION);
            FileOutputStream fos = null;

            try {
                fos = new FileOutputStream(iconFile);
                bm.compress(Bitmap.CompressFormat.PNG, BITMAP_COMPRESS_QUALITY, fos);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                // Hvis exception skjer, returner null:
                iconFile = null;
            }
            finally {
                if (fos != null) {
                    try {
                        fos.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            return iconFile;
        }

        // Returner null hvis fil eller mappe ikke kunne opprettes:
        else {
            return null;
        }
    }

    // Les ikon fra fil:
    public Bitmap readIconBitmapFromFile(File file) {
        if (file != null && file.exists()) {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        }
        else {
            return null;
        }
    }

    // Sjekker om lagringsmedium er tilgjengelig:
    private boolean isExtStorageAvailable() {
        String state = Environment.getExternalStorageState();

        return state.equals(Environment.MEDIA_MOUNTED);
    }
}
