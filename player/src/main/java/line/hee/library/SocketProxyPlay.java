package line.hee.library;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import line.hee.library.tools.L;


/**
 * Created by hacceee on 2017/1/9.
 * 如果要mediaplayer去播放实现预加载和边下边播的话，我们要手动实现一个代理服务器，代理到本地，也就是127.0.0.1
 * 然后mediaplayer去访问的时候就直接请求我们的本地代理服务器
 * ，然后使用另外一个socket(或者http)模拟mediaplayer发送数据，返回的结果手动设置到监听到的代理client socket中
 */

public  class SocketProxyPlay {

    private static String TAG = "SocketProxyPlay";

    private static final int DEFAULT_POOL_SIZE = 3;

	private static final String PROXY_HOST = "127.0.0.1";
	private static final int PROXY_SERVER_TIME_OUT = 15 * 1000;
	private static final int PROXY_PORT = 8123;
	// private HostInfo mProxyHost;
	private ServerSocket mProxyServerSocket;
	// private Socket mRemoteSocket;
	// private MediaPlayer mMediaPlayer;
	
	//持有applicationContext 的变量，防止泄露内存
//	private Context mAppContext;

    private ListeningRequest mListeningRequest;

    private Socket mThisClient;

    private  File mSavePlayDir;

    private static SocketProxyPlay mInstance;

    // private static Executor mProxyClientExecutors;
    // private static BlockingQueue<Runnable> proxyClientQueue = new
    // LinkedBlockingQueue<>();

    // private static BlockingQueue<String> mString =

    private Thread mThread;

    public static SocketProxyPlay getInstance() {
        L.d(TAG, "socket play instance:" + mInstance);
        if(mInstance == null){
            synchronized (SocketProxyPlay.class) {
                if (mInstance == null) {
                    mInstance = new SocketProxyPlay();
                }
            }
        }
        return mInstance;

    }

	public void listening() {
		if (mThread == null || mThread.isInterrupted()) {
			mThread = null;
			mListeningRequest = null;
			reset();
		}
	}
	
	private void reset(){
		mListeningRequest = new ListeningRequest();
		mThread = new Thread(mListeningRequest);
		mThread.start();
	}

	private  SocketProxyPlay() {
	}

	public void init(Context context, boolean listen) {
		// mProxyHost = new HostInfo(PROXY_HOST, 80);
//		mAppContext = context.getApplicationContext();
		if(listen){
			listening();
		}
		createDefaultSavePath(context);
	}

	// public void setMdiaPlayer(MediaPlayer m){
	// this.mMediaPlayer = m;
	// }

	/**
	 * 如果是http的请求，直接代理掉
	 * 
	 * @param url
	 */
	public void play(String url, @NonNull MediaPlayer player) {
		L.d( "play url:" + url);
		if (matchHttp(url)) {
			try {
				L.d( "url:" + url + "to proxy");
				mListeningRequest.mUrl = url;
				player.setDataSource("http://" + PROXY_HOST + ":" + PROXY_PORT);
				player.prepareAsync();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			try {
				player.setDataSource(url);
				player.prepareAsync();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	class ListeningRequest implements Runnable {

		private String mUrl;
		private boolean mSocketNeedClose = false;

		ListeningRequest() {
		}

		@Override
		public void run() {
			if (mProxyServerSocket == null || mProxyServerSocket.isClosed()) {
				// 初始化
				try {
					/*
					 * 代理服务器建立，监听mediaplayer的请求
					 */
					mProxyServerSocket = new ServerSocket(PROXY_PORT, 0,
							InetAddress.getByName(PROXY_HOST));
					mProxyServerSocket.setSoTimeout(PROXY_SERVER_TIME_OUT);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					L.e( "you are init proxy socket failed：" + e.getMessage());
					return;
				}
			}
			InputStream cacheIs = null;
			Socket client = null;
			OutputStream clientOs = null;
			while (!Thread.currentThread().isInterrupted() && !mSocketNeedClose) {
				try {
					client = mProxyServerSocket.accept();
					L.d( "accept request start, client:" + client);
					mHandler.sendEmptyMessage(PLAY_PREPARE);
					if (!matchHttp(mUrl)) {
						String errMsg = "you display is not an right url!";
						mHandler.obtainMessage(PLAY_FALIED, errMsg)
								.sendToTarget();
						return;
					}
					if (client == null) {
						mHandler.sendEmptyMessage(PLAY_FALIED);
						return;
					}
					mThisClient = client;
//					System.setProperty("http.keepAlive", "false");
					HostInfo remoteHost = convertRequest(client, mUrl);
					// 由于converRequest执行完毕若是Range的file就被删除了，所以在这个方法执行完毕之后，需要读取缓存，如果缓存还在，那么说明这里缓存是整块的，可以直接返回
					// 先读取缓存
					cacheIs = readCache(mUrl);
					L.d( "read cache start");
					if (cacheIs != null) {
						clientOs = client.getOutputStream();
						int readByte = -1;
						byte[] buf = new byte[1024];
						int total = cacheIs.available();
						int readLenght = 0;
						while ((readByte = cacheIs.read(buf, 0, buf.length)) != -1) {
							clientOs.write(buf, 0, readByte);
							readLenght += readByte;
							int[] readP = new int[2];
							readP[0] = total;
							readP[1] = readLenght;
							L.d( "read cache length:" + readLenght + "...total:"
									+ total);
							mHandler.obtainMessage(PLAY_LOADING, readP)
									.sendToTarget();
						}
						L.d( "read cache ok");
						clientOs.flush();
						clientOs.close();
						cacheIs.close();
						client.close();
						mHandler.sendEmptyMessage(PLAY_COMPLETE);

					} else {
						Socket remoteSocket = sendRemoteRequest(remoteHost);
						resRequest(client, remoteSocket, remoteHost);
					}
				} catch (IOException e) {
					L.e( "listeneing url exception:" + e.getMessage());
//				} catch (InterruptedException e) {
//					L.e( "take url InterruptedException:" + e.getMessage());
//					e.printStackTrace();
				} finally {
					if (cacheIs != null) {
						try {
							cacheIs.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (client != null && !client.isClosed()) {
						try {
							client.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

				}
			}
			try {
				if(mProxyServerSocket != null){
				mProxyServerSocket.close();
				mProxyServerSocket = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * @author hacceee 获取到给代理服务器里的请求内容，然后转到真实的socket上进行转发请求
	 */
	public HostInfo convertRequest(Socket client, String remoteHost) {
		L.d( "convertRequest execute");
		byte[] buffer = new byte[512];
		HostInfo remoteHostInfo = null;
		int readByte = -1;
		String params = null;
		InputStream clientInputStream = null;
		try {
			clientInputStream = client.getInputStream();
			while (true) {
				readByte = clientInputStream.read(buffer);
				if (readByte == -1) {
					break;
				}
				if (params == null) {
					params = "";
				}

				params += new String(buffer, "UTF-8");
				if (params.toString().contains("GET")
						&& params.contains("\r\n\r\n")) {
					if (remoteHostInfo == null) {
						remoteHostInfo = new HostInfo(remoteHost);
					}
					Pattern p = Pattern
							.compile("(GET)(((?!HTTP/1.1).)*)((HTTP/1.1)?\\r\\n)[\\d\\D]*");
					Matcher m = p.matcher(params);
					if (m.matches()) {
						params = params.replace(m.group(2), " " + remoteHost
								+ " ");
					}
					params = params.replaceAll(PROXY_HOST + ":" + PROXY_PORT,
							remoteHostInfo.ip + ":" + remoteHostInfo.port);
					if (params.contains("Range")) {
						L.d( "Range to delete file:" + params);
						// 删除缓存
						File cacheFile = getCacheFile(remoteHostInfo.url);
						if (cacheFile != null && cacheFile.exists()) {
							cacheFile.delete();
						}
						remoteHostInfo.allowCache = false;
					} else {
						remoteHostInfo.allowCache = true;
					}
					remoteHostInfo.requestParams = params;
					break;
				}

			}

		} catch (IOException e) {
			L.e( e.getMessage());
		} catch (Exception e) {
			L.e( e.getMessage());
		} finally {
			// try {
			// if(clientInputStream != null){
			// clientInputStream.close();
			// }
			// }catch (IOException e) {
			// e.printStackTrace();
			// }
		}
		return remoteHostInfo;

	}

	/**
	 * 将模拟mediaplayer的请求返回数据写入代理服务器，返回给mediaplayer
	 */
	private void resRequest(Socket client, Socket remoteSocket,
			HostInfo remoteHostInfo) {
		L.d( "resRequest start");
		InputStream remoteIs = null;
		OutputStream clientOs = null;
		FileOutputStream fileOutputStream = null;
		try {
			remoteIs = remoteSocket.getInputStream();
			if (remoteIs == null) {
				return;
			}
			if (mThisClient == null || mThisClient != client) {
				return;
			}
			clientOs = client.getOutputStream();
			byte[] buf = new byte[1024];
			int readByteLength = -1;
			int writeStreamLength = 0;
			int total = remoteIs.available();
			while ((readByteLength = remoteIs.read(buf, 0, buf.length)) != -1) {
				writeStreamLength += readByteLength;
				clientOs.write(buf, 0, readByteLength);
				clientOs.flush();
				int[] writeB = new int[2];
				writeB[0] = total;
				writeB[1] = writeStreamLength;
				mHandler.obtainMessage(PLAY_LOADING, writeB).sendToTarget();
				if (remoteHostInfo.allowCache) {
					if (fileOutputStream == null) {
						File cacheFile = getCacheFile(remoteHostInfo.url);
						if (cacheFile != null) {
							if (!cacheFile.exists()) {
								cacheFile.createNewFile();
							}
							fileOutputStream = new FileOutputStream(cacheFile);
						}

					}
					fileOutputStream.write(buf, 0, readByteLength);
				}
				if (readByteLength < buf.length) {
					String redirectStr = new String(buf);

					L.d( "reponse stream :"+ redirectStr);
					if (redirectStr.contains("HTTP")) {
						int responseCode = getResponseCode(redirectStr);
						if (responseCode >= 300) {
							remoteIs.close();
							remoteSocket.close();
							if(fileOutputStream != null){
								fileOutputStream.flush();
								fileOutputStream.close();
							}
							delCache(remoteHostInfo.url);

							// 若遇到302重定向,递归直到遇到真正的请求地址
							if ((responseCode == 302 || redirectStr
									.contains("302"))
									&& redirectStr.contains("Location:")) {
								L.d( "redirect url:" + redirectStr);
								// 关闭之前打开的流和socket并且删除之前的缓存
								int subStart = redirectStr.indexOf("Location:")
										+ "Location:".length();
								String url = redirectStr.substring(subStart,
										redirectStr.indexOf("\r\n", redirectStr
												.indexOf("Location:")));
								HostInfo hostInfo = new HostInfo(url);
								hostInfo.requestParams = remoteHostInfo.requestParams;
								Pattern p = Pattern
										.compile("(GET)(((?!HTTP/1.\\d).)*)((HTTP/1.\\d)?\\r\\n)[\\d\\D]*");
								Matcher m = p.matcher(hostInfo.requestParams);
								if (m.matches()) {
									hostInfo.requestParams = hostInfo.requestParams
											.replace(m.group(2), " " + url
													+ " ");
								}
								Socket rso = sendRemoteRequest(hostInfo);
								if (rso != null) {
									resRequest(client, rso, remoteHostInfo);
									return;
								}

							}
							client.close();
							clientOs.close();
							return;
						}
					}
				}

				

			}
			mHandler.sendEmptyMessage(PLAY_COMPLETE);
		} catch (IOException e) {
			remoteHostInfo.allowCache = false;
			delCache(remoteHostInfo.url);
			L.e( "resRequest falied! IOException:" + e.getMessage());
		} catch (Exception e) {
			L.e( "resRequest failed Exception");
			remoteHostInfo.allowCache = false;
			delCache(remoteHostInfo.url);
		} finally {
			try {
				if (remoteIs != null) {
					remoteIs.close();
				}
				if (clientOs != null) {
					clientOs.flush();
					clientOs.close();
				}
				if (fileOutputStream != null) {
					fileOutputStream.flush();
					fileOutputStream.close();
				}
				client.close();
				remoteSocket.close();
			} catch (Exception e) {

			}
		}
	}

	/**
	 * 模拟mediaplayer 发送请求
	 * 
	 * @param remoteInfo
	 * @return
	 */
	private Socket sendRemoteRequest(HostInfo remoteInfo) {
		Socket remoteSocket = null;
		try {
			remoteSocket = new Socket();
			remoteSocket.connect(new InetSocketAddress(remoteInfo.ip,
					remoteInfo.port));
			remoteSocket.getOutputStream().write(
					remoteInfo.requestParams.getBytes("UTF-8"));
			remoteSocket.getOutputStream().flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return remoteSocket;
	}

	/**
	 * 读缓存
	 * 
	 * @param
	 * @return
	 */
	@Nullable
	private InputStream readCache(String url) {
		if (mSavePlayDir == null) {
			throw new NullPointerException("you must be init at first");
		}
		File file = getCacheFile(url);
		if (!file.exists()) {
			return null;
		}
		FileInputStream fileIs = null;
		try {
			fileIs = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return fileIs;
	}

	private File getCacheFile(String url) {
		String name = createFileName(url);
		return new File(mSavePlayDir, name);
	}

	public static String createFileName(String url) {
		String name = Base64.encodeToString(url.getBytes(), Base64.DEFAULT).replaceAll("/", "");
		return name;
	}

	private boolean matchHttp(String url) {
		Pattern pattern = Pattern
				.compile("^((https|http|ftp|rtsp|mms)?:\\/\\/)[^\\s]+");
		return pattern.matcher(url).find();
	}

	public static int matchPort(String url) {
		int port = -1;
		if (TextUtils.isEmpty(url)) {
			return port;
		}
		Pattern pattern = Pattern.compile("^:[\\d]+$");
		Matcher matcher = pattern.matcher(url);
		if (matcher.find()) {
			port = Integer.valueOf(pattern.toString().substring(
					matcher.start() + 1, matcher.end()));
		}
		return port;
	}

	public static String matchHost(String url) {
		Pattern pattern = Pattern.compile("[\\d\\D]?(http[s]?://)?([\\d|\\.|\\w]+)((:[\\d]+)?)/");
		Matcher matcher = pattern.matcher(url);
		String host = url;
		if (matcher.find()) {
			int start = 0;
			Pattern httpf = Pattern.compile("[\\d\\D]?http[s]?://");
			Matcher m = httpf.matcher(url);
			if (m.find()) {
				start = m.end();
			}
			int end = matcher.end() - 1;

			pattern = Pattern.compile(":[\\d]+");
			matcher = pattern.matcher(matcher.group());
			if (matcher.find()) {
				end = matcher.start();
			}
			host = url.substring(start, end);
		}
		return host;

	}

	/**
	 * 从返回的信息中抓取返回的状态码
	 * 
	 * @author hacceee
	 * @return
	 */
	public static int getResponseCode(String resMessage) {
		if (TextUtils.isEmpty(resMessage)) {
			return 10086;
		}
		Pattern p = Pattern
				.compile("^([\\s\\w\\d\\./]+[\\s]+)([\\d]+)([\\s|\\w]+\\r\\n)");
		Matcher matcher = p.matcher(resMessage);
		if (matcher.find()) {
			return Integer.valueOf(matcher.group(2));
		}
		return 10086;
	}

	/**
	 * 存储相应请求相关的地址和端口及其他信息的Model
	 */
	public static class HostInfo {

		public HostInfo(String ip, int port) {
			this.ip = ip;
			this.port = port;
		}

		public HostInfo(String url) {
			this.url = url;
			ip = matchHost(url);
			port = matchPort(url);
			if (port <= 0) {
				port = 80;
			}
			// fileName = createFileName(url);
		}

		public String url;
		public String ip;
		public int port;
		public String requestParams;
		public boolean allowCache = false;
		// public String fileName;
	}

	// public interface OnReadMediaListener{
	// void onPrepare();
	// void onLoading(long readBytes, long totalBytes);
	// void onLoadComplete();
	// void onLoadFailed(String errMsg);
	// }

	// private static List<OnReadMediaListener> mediaListeners = new
	// ArrayList<>();
	//
	// public static void addReadMediaListener(OnReadMediaListener listener){
	// mediaListeners.add(listener);
	// }
	//
	// public static void removeMediaListener(OnReadMediaListener listener){
	// mediaListeners.remove(listener);
	// }

	// private OnReadMediaListener mReadMediaListener;
	// public void setReadMediaListener(OnReadMediaListener listener){
	// mReadMediaListener = listener;
	// }

	// public static void init(Context context){
	// mProxyClientExecutors = new ThreadPoolExecutor(DEFAULT_POOL_SIZE, 256,
	// 600, TimeUnit.SECONDS, proxyClientQueue, new ProxyClientFactory());
	// }

	public static class ProxyClientFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r);
		}
	}

	public  void createDefaultSavePath(Context context) {
		mSavePlayDir = new File(StorageUtils.getCacheDirectory(context, true),
				"/proxyMedia");
		if (!mSavePlayDir.exists()) {
			mSavePlayDir.mkdir();
		}
	}

	public  void setCacheDir(String dir) {
		mSavePlayDir = new File(dir + "/proxyMedia");
	}
	
	public File getCachePath(){
		return mSavePlayDir;
	}

	private void delCache(String url) {
		L.d( "delCache start");
		File cacheFile = getCacheFile(url);
		if (cacheFile != null && cacheFile.exists()) {
			cacheFile.delete();
		}
	}

	private static final int PLAY_PREPARE = 0x1;
	private static final int PLAY_LOADING = 0x2;
	private static final int PLAY_COMPLETE = 0x3;
	private static final int PLAY_FALIED = 0x4;

	public Handler mHandler = new SocketProxyHandler(this);

	/*
	 * handler
	 */
	class SocketProxyHandler extends Handler {

		/**
		 * 持有这个类的弱引用，这样能避免发生内存泄露
		 * 
		 * @param msg
		 */
		WeakReference<SocketProxyPlay> mH;

		SocketProxyHandler(SocketProxyPlay h) {
			mH = new WeakReference<SocketProxyPlay>(h);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case PLAY_PREPARE:
				// if(mH.get().mReadMediaListener != null){
				// mH.get().mReadMediaListener.onPrepare();
				// }

				break;
			case PLAY_LOADING:
				// if(mH.get().mReadMediaListener != null){
				// int[] loadingP = (int[]) msg.obj;
				// if(loadingP == null){
				// throw new
				// NullPointerException("you must write this param loading");
				// }
				// mH.get().mReadMediaListener.onLoading(loadingP[0],
				// loadingP[1]);
				// }
				break;
			case PLAY_FALIED:
				// if(mH.get().mReadMediaListener != null){
				// String errMsg = "";
				// if(msg.obj != null){
				// errMsg = (String) msg.obj;
				// }
				// mH.get().mReadMediaListener.onLoadFailed(errMsg);
				// }
				break;
			case PLAY_COMPLETE:
				// if(mH.get().mReadMediaListener != null){
				// mH.get().mReadMediaListener.onLoadComplete();
				// }
				break;
			default:
				break;
			}
		}
	}

	public void close() {
		if (mThisClient != null && !mThisClient.isClosed()) {
			try {
				mThisClient.close();
			} catch (IOException e) {
				// L.e( msg);
			} finally {
				// mThisClient = null;
			}
		}

		// try {
		// if (AspLog.isPrintLog) {
		// AspLog.d("close server socket:" + mProxyServerSocket);
		// }
		// if (mProxyServerSocket != null) {
		// mProxyServerSocket.close();
		// }
		// } catch (IOException e) {
		//
		// } finally {
		// // mProxyServerSocket = null;
		// }
		mListeningRequest.mSocketNeedClose = true;
		mThread.interrupt();
		mThread = null;
		mListeningRequest = null;

	}

}
