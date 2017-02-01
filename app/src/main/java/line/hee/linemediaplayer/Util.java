package line.hee.linemediaplayer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import line.hee.library.StorageUtils;

/**
 * Created by Administrator on 2017/1/17.
 */

public class Util {

    private static String[] PERMISSIONS_STORAGE = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private static final String EXTERNAL_STORAGE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE";
    public static final int REQUEST_EXTERNAL_STORAGE = 1;

    public static void checkAndRequestStoragePermission(Activity a){
        if(!hasExternalStoragePermission(a) && Build.VERSION.SDK_INT >= 23){
            a.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);

        }
    }

    private static boolean hasExternalStoragePermission(Context context) {
        int perm = context.checkCallingOrSelfPermission(EXTERNAL_STORAGE_PERMISSION);
        return perm == PackageManager.PERMISSION_GRANTED;
    }
}
