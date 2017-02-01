package line.hee.linemediaplayer;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;

import line.hee.library.SocketProxyPlay;
import line.hee.library.StorageUtils;

/**
 * Created by hacceee on 2017/1/24.
 */

public class DemoActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener{

    private static String TAG = "DemoActivity";

    private static String[] PlayUrls = new String[]{"http://mp3-cdn.luoo.net/low/luoo/radio889/01.mp3 ",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/02.mp3 ",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/03.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/04.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/05.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/06.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/07.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/08.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/09.mp3",
            "http://mp3-cdn.luoo.net/low/luoo/radio889/10.mp3"};

//    private SimpleDateFormat mProgressFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private int mPlayingIndex = 0;


    SeekBar mSeekBar;
    MediaPlayer mMediaPlayer;
    private String mPreparedUrl;

    private View mPlayBtn;

    TextView mTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.checkAndRequestStoragePermission(this);
        setContentView(R.layout.activity_demo);
        mSeekBar = (SeekBar)findViewById(R.id.seekBar2);
        mSeekBar.setOnSeekBarChangeListener(this);
        findViewById(R.id.previousPlay).setOnClickListener(this);
        findViewById(R.id.nextPlay).setOnClickListener(this);
        mPlayBtn = findViewById(R.id.play);
        mPlayBtn.setOnClickListener(this);
        mTitle = (TextView) findViewById(R.id.title);
        mPreparedUrl = PlayUrls[0];
        mTitle.setText(mPreparedUrl);
        initMediaPlayer();

    }

    /**
     * 初始化播放器
     */
    private void initMediaPlayer(){
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {

                if(mPlayBtn.isSelected()) {
                    Log.d(TAG, "prepare ok");
                    mMediaPlayer.start();
                    mHander.removeCallbacks(mProgressRun);
                    mHander.post(mProgressRun);
                }
                mSeekBar.setSelected(false);
                mPreparedUrl = null;
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {

            }
        });

        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(DemoActivity.this, "播放失败。", Toast.LENGTH_SHORT).show();
                stop();
                return false;
            }
        });
    }

    private void resetPlay(String url){
       stop();
        mPreparedUrl = url;
        SocketProxyPlay.getInstance().play(url, mMediaPlayer);
        mSeekBar.setSelected(true);
       Drawable drawable =  mSeekBar.getThumb().getCurrent();
        if(drawable instanceof AnimationDrawable){
            ((AnimationDrawable)drawable).start();
        }

    }

    private void stop(){
        if(mMediaPlayer != null){
            mMediaPlayer.stop();
            mMediaPlayer.reset();
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play:
                v.setSelected(!v.isSelected());
                if(v.isSelected()){
                    if(!TextUtils.isEmpty(mPreparedUrl)) {
                        resetPlay(mPreparedUrl);
                    }else{
                        mMediaPlayer.start();
                    }
                }else if(mMediaPlayer.isPlaying()){
                    mMediaPlayer.pause();
                }
                break;
            case R.id.nextPlay:
                if(mPlayingIndex >= PlayUrls.length - 1){
                    mPlayingIndex = 0;
                }else{
                    mPlayingIndex += 1;
                }
                String url = PlayUrls[mPlayingIndex];
                mPlayBtn.setSelected(true);
                resetPlay(url);
                mTitle.setText(url);
                break;
            case R.id.previousPlay:
                if(mPlayingIndex <= 0){
                    mPlayingIndex = PlayUrls.length - 1;
                }else{
                    mPlayingIndex -= 1;
                }
                mPlayBtn.setSelected(true);
                String pUrl = PlayUrls[mPlayingIndex];
                resetPlay(pUrl);
                mTitle.setText(pUrl);
                break;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        seekBar.setTag(R.id.tag_perversion_progress, seekBar.getProgress());
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if(seekBar.isSelected()){
            seekBar.setProgress((Integer) seekBar.getTag(R.id.tag_perversion_progress));
            return;
        }
        int durationTotal = mMediaPlayer.getDuration();
        int percent = (int) ((double) seekBar.getProgress() / 100 * durationTotal);
        mMediaPlayer.seekTo(percent);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 1){
            SocketProxyPlay.getInstance().createDefaultSavePath(this);
        }
    }

    private Runnable mProgressRun = new Runnable() {
        @Override
        public void run() {
            if(mMediaPlayer.isPlaying()) {
                int percent = (int)(100 * ((double)mMediaPlayer.getCurrentPosition() / mMediaPlayer.getDuration()));
                mSeekBar.setProgress(percent);
                mHander.postDelayed(this, 100);
            }
        }
    };

    private final Handler mHander = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };
}
