package line.hee.linemediaplayer;

import android.app.Application;
import android.os.Environment;

import line.hee.library.PlayConfigature;
import line.hee.library.SocketProxyPlay;
import line.hee.library.StorageUtils;
import line.hee.library.tools.L;

/**
 * Created by Administrator on 2017/1/16.
 */

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        L.writeLogs(true);
        SocketProxyPlay.getInstance().init(this, true);
    }
}
